package com.intellipolis.traffic;

import com.intellipolis.common.config.ExternalHttp;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
public class TrafficDataService {
 private static final String LINK_URL="https://apis.data.go.kr/6260000/BusanITSLINKTraffic/LINKTrafficList";
 private static final String INTERSECTION_URL="https://apis.data.go.kr/6260000/BusanITSSINTACR/ACRTrf";
 private final String publicKey; private final String itsKey; private final RoadGeometryService geometry;
 private final RestClient http=ExternalHttp.create(); private final Map<String,TrafficSummary> cache=new ConcurrentHashMap<>(); private final Map<String,JsonNode> apiCache=new ConcurrentHashMap<>();
 public TrafficDataService(@Value("${app.public-data.api-key:}")String publicKey,@Value("${app.its.api-key:}")String itsKey,RoadGeometryService geometry){this.publicKey=publicKey;this.itsKey=itsKey;this.geometry=geometry;}
 public Map<String,Object> status(){return Map.of("configured",!publicKey.isBlank()||!itsKey.isBlank(),"provider",!publicKey.isBlank()?"부산광역시 링크소통·스마트교차로":"국가교통정보센터(대체)");}
 public TrafficSummary summary(String city,String district,double longitude,double latitude){
  if(!"부산광역시".equals(city))throw new TrafficDataException("현재 교통 분석 범위는 부산광역시입니다.");
  if(publicKey.isBlank())return nationalFallback(city,district,longitude,latitude);
  String hour=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
  return cache.computeIfAbsent(district+hour,k->busan(city,district,longitude,latitude,hour));
 }
 private TrafficSummary busan(String city,String district,double lon,double lat,String hour){
  try{
   JsonNode links=get(LINK_URL,Map.of("pageNo","1","numOfRows","10000"));
   JsonNode intersections=get(INTERSECTION_URL,Map.of("pageNo","1","numOfRows","1000","yyyyMMdd",hour.substring(0,8),"hour",hour.substring(8)));
   List<LinkRow> all=new ArrayList<>();JsonNode items=links.path("content").path("items");
   if(items.isArray())for(JsonNode n:items)all.add(new LinkRow(text(n,"lkId"),text(n,"roadNm"),text(n,"bgngNodeNm"),text(n,"endNodeNm"),number(n,"spd"),number(n,"vol")));
   Map<String,IntersectionRow> ix=intersections(intersections);
   List<LinkRow> slow=all.stream().filter(x->x.speed()<20).sorted(Comparator.comparingDouble(LinkRow::speed)).toList();
   Map<String,List<List<Double>>> shapes=geometry.coordinates(slow.stream().map(LinkRow::id).collect(java.util.stream.Collectors.toSet()));
   List<RoadSpeed> nearby=slow.stream().filter(x->inArea(shapes.get(x.id()),lon,lat)).limit(20).map(x->road(x,ix,shapes.get(x.id()))).toList();
   if(nearby.isEmpty())nearby=slow.stream().limit(20).map(x->road(x,ix,shapes.get(x.id()))).toList();
   double average=all.stream().mapToDouble(LinkRow::speed).filter(x->x>0).average().orElse(0);
   return new TrafficSummary(city,district,all.size(),round(average),(int)all.stream().filter(x->x.speed()<20).count(),nearby,
    List.of("부산 링크소통 실측 속도와 스마트교차로 관측값을 결합했습니다.","좌표가 없는 링크는 지도 계획선에서 제외됩니다."));
  }catch(Exception e){if(!itsKey.isBlank())return nationalFallback(city,district,lon,lat);throw new TrafficDataException("부산 교통정보 API 호출에 실패했습니다.");}
 }
 private RoadSpeed road(LinkRow x,Map<String,IntersectionRow> intersections,List<List<Double>> coordinates){
  IntersectionRow match=intersections.values().stream().filter(i->same(i.name(),x.start())||same(i.name(),x.end())).findFirst().orElse(null);
  return new RoadSpeed(x.id(),blank(x.road(),"도로명 없음"),round(x.speed()),round(x.volume()),match==null?null:match.name(),match==null?null:round(match.queue()),match==null?null:match.walk(),coordinates==null?List.of():coordinates);
 }
 private Map<String,IntersectionRow> intersections(JsonNode root){Map<String,IntersectionRow> out=new LinkedHashMap<>();JsonNode items=root.path("content").path("items");if(items.isArray())for(JsonNode n:items){String name=text(n,"ixrNm");double volume=number(n,"lrgGoTfvl")+number(n,"lrgLeftTfvl")+number(n,"lrgRghtTfvl")+number(n,"mddlGoTfvl")+number(n,"mddlLeftTfvl")+number(n,"mddlRghtTfvl")+number(n,"smalGoTfvl")+number(n,"smalLeftTfvl")+number(n,"smalRghtTfvl");out.put(name+text(n,"istlLctn"),new IntersectionRow(name,volume,number(n,"intlAtmpHeatLngt"),(long)number(n,"walkCnt")));}return out;}
 private JsonNode get(String base,Map<String,String> params){var b=UriComponentsBuilder.fromUriString(base).queryParam("serviceKey",publicKey).queryParam("resultType","json");params.forEach(b::queryParam);URI uri=b.build(true).toUri();return apiCache.computeIfAbsent(uri.toString(),key->{JsonNode body=http.get().uri(uri).retrieve().body(JsonNode.class);if(body==null||!"00".equals(body.path("resultCode").asText()))throw new TrafficDataException("부산 교통정보 응답이 올바르지 않습니다.");return body;});}
 private TrafficSummary nationalFallback(String city,String district,double longitude,double latitude){
  if(itsKey.isBlank())throw new TrafficDataException("부산 교통 API 키가 설정되지 않았습니다.");
  URI uri=UriComponentsBuilder.fromUriString("https://openapi.its.go.kr:9443/trafficInfo").queryParam("apiKey",itsKey).queryParam("type","all").queryParam("getType","json").queryParam("minX",longitude-.06).queryParam("maxX",longitude+.06).queryParam("minY",latitude-.05).queryParam("maxY",latitude+.05).build().encode().toUri();
  try{JsonNode body=http.get().uri(uri).retrieve().body(JsonNode.class);List<RoadSpeed> roads=new ArrayList<>();collect(body,roads);double avg=roads.stream().mapToDouble(RoadSpeed::speedKmh).average().orElse(0);var slow=roads.stream().filter(x->x.speedKmh()<20).limit(20).toList();return new TrafficSummary(city,district,roads.size(),round(avg),slow.size(),slow,List.of("부산 API 응답 실패로 국가 ITS 속도 정보를 사용했습니다.","국가 ITS 응답에는 계획선 좌표가 없어 도로 대안을 지도에 표시하지 않습니다."));}catch(Exception e){throw new TrafficDataException("교통정보 API 호출에 실패했습니다.");}
 }
 private void collect(JsonNode n,List<RoadSpeed> out){if(n==null)return;if(n.isArray()){n.forEach(x->collect(x,out));return;}if(!n.isObject())return;String id=text(n,"linkId","linkid","linkNo"),s=text(n,"speed","spd");if(id!=null&&s!=null)try{out.add(new RoadSpeed(id,blank(text(n,"roadName","roadname","roadNm"),"도로명 없음"),Double.parseDouble(s),0,null,null,null,List.of()));}catch(NumberFormatException ignored){}n.properties().forEach(x->collect(x.getValue(),out));}
 private boolean inArea(List<List<Double>> c,double lon,double lat){return c!=null&&c.stream().anyMatch(p->p.size()>1&&Math.abs(p.get(0)-lon)<.08&&Math.abs(p.get(1)-lat)<.06);}
 private boolean same(String a,String b){return a!=null&&b!=null&&!a.isBlank()&&!b.isBlank()&&(a.contains(b)||b.contains(a));}
 private String text(JsonNode n,String...names){for(String name:names){JsonNode v=n.get(name);if(v!=null&&!v.isNull()&&!v.asText().isBlank())return v.asText();}return null;}
 private double number(JsonNode n,String name){JsonNode v=n.get(name);return v==null||v.isNull()?0:v.asDouble();}
 private String blank(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
 private double round(double x){return Math.round(x*10)/10d;}
 private record LinkRow(String id,String road,String start,String end,double speed,double volume){}
 private record IntersectionRow(String name,double volume,double queue,long walk){}
 public record RoadSpeed(String linkId,String roadName,double speedKmh,double volume,String intersectionName,Double queueLength,Long pedestrianCount,List<List<Double>> coordinates){}
 public record TrafficSummary(String cityName,String districtName,int measuredLinkCount,double averageSpeedKmh,int congestedLinkCount,List<RoadSpeed> congestedRoads,List<String>warnings){}
}
