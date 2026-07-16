package com.intellipolis.traffic;

import com.intellipolis.common.config.ExternalHttp;
import com.intellipolis.spatial.SpatialDataService;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

@Service
public class TrafficDataService {
 private static final Logger log=LoggerFactory.getLogger(TrafficDataService.class);
 private static final String LINK_URL="https://apis.data.go.kr/6260000/BusanITSLINKTraffic/LINKTrafficList";
 private static final String INTERSECTION_URL="https://apis.data.go.kr/6260000/BusanITSSINTACR/ACRTrf";
 private final String publicKey; private final String itsKey; private final RoadGeometryService geometry; private final SpatialDataService spatial;
 private final RestClient http=ExternalHttp.create(); private final Map<String,TrafficSummary> cache=new ConcurrentHashMap<>(); private final Map<String,JsonNode> apiCache=new ConcurrentHashMap<>();
 public TrafficDataService(@Value("${app.public-data.api-key:}")String publicKey,@Value("${app.its.api-key:}")String itsKey,RoadGeometryService geometry,SpatialDataService spatial){this.publicKey=publicKey;this.itsKey=itsKey;this.geometry=geometry;this.spatial=spatial;}
 public Map<String,Object> status(){return Map.of("configured",!publicKey.isBlank()||!itsKey.isBlank(),"provider",!itsKey.isBlank()?"국가교통정보센터 ITS·국가표준 도로형상":"부산광역시 링크소통·스마트교차로");}
 public TrafficSummary summary(String city,String district,double longitude,double latitude){
  if(!"부산광역시".equals(city))throw new TrafficDataException("현재 교통 분석 범위는 부산광역시입니다.");
  String hour=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
  if(!itsKey.isBlank())return cache.computeIfAbsent(district+hour,k->nationalFallback(city,district,longitude,latitude));
  if(!publicKey.isBlank())return cache.computeIfAbsent(district+hour,k->busan(city,district,longitude,latitude,hour));
  throw new TrafficDataException("교통정보 API 키가 설정되지 않았습니다.");
 }
 private TrafficSummary busan(String city,String district,double lon,double lat,String hour){
  try{
   var linksFuture=CompletableFuture.supplyAsync(this::links);
   var intersectionsFuture=CompletableFuture.supplyAsync(()->intersectionPages(hour));
   List<LinkRow> all=linksFuture.join();List<JsonNode> intersectionPages=intersectionsFuture.join();
   if(intersectionPages.getFirst().path("content").path("totalCount").asInt()==0){
    String previous=LocalDateTime.parse(hour,DateTimeFormatter.ofPattern("yyyyMMddHH")).minusHours(1).format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    intersectionPages=intersectionPages(previous);
   }
   Map<String,IntersectionRow> ix=intersections(intersectionPages);
   Map<String,List<List<Double>>> shapes=geometry.coordinates(all.stream().map(LinkRow::id).collect(java.util.stream.Collectors.toSet()));
   List<LinkRow> area=all.stream().filter(x->inDistrict(shapes.get(x.id()),city,district,lon,lat)).toList();
   List<LinkRow> slow=area.stream().filter(x->x.speed()<20).sorted(Comparator.comparingDouble(LinkRow::speed)).toList();
   List<RoadSpeed> nearby=slow.stream().limit(20).map(x->road(x,ix,shapes.get(x.id()))).toList();
   double average=area.stream().mapToDouble(LinkRow::speed).filter(x->x>0).average().orElse(0);
   return new TrafficSummary(city,district,area.size(),round(average),slow.size(),nearby,
    List.of("부산 링크소통 실측 속도와 스마트교차로 관측값을 결합했습니다.","좌표가 없는 링크는 지도 계획선에서 제외됩니다."));
  }catch(Exception e){if(!itsKey.isBlank()){log.warn("부산 교통정보 처리 실패, 국가 ITS로 대체: {}",district,e);return nationalFallback(city,district,lon,lat);}throw new TrafficDataException("부산 교통정보 API 호출에 실패했습니다.");}
 }
 private RoadSpeed road(LinkRow x,Map<String,IntersectionRow> intersections,List<List<Double>> coordinates){
  IntersectionRow match=intersections.values().stream().filter(i->same(i.name(),x.start())||same(i.name(),x.end())).findFirst().orElse(null);
  return new RoadSpeed(x.id(),blank(x.road(),"도로명 없음"),round(x.speed()),round(x.volume()),match==null?null:match.name(),match==null?null:round(match.queue()),match==null?null:match.walk(),coordinates==null?List.of():coordinates);
 }
 private Map<String,IntersectionRow> intersections(JsonNode root){Map<String,IntersectionRow> out=new LinkedHashMap<>();JsonNode items=root.path("content").path("items");if(items.isArray())for(JsonNode n:items){String name=text(n,"ixrNm");double volume=number(n,"lrgGoTfvl")+number(n,"lrgLeftTfvl")+number(n,"lrgRghtTfvl")+number(n,"mddlGoTfvl")+number(n,"mddlLeftTfvl")+number(n,"mddlRghtTfvl")+number(n,"smalGoTfvl")+number(n,"smalLeftTfvl")+number(n,"smalRghtTfvl");out.put(name+text(n,"istlLctn"),new IntersectionRow(name,volume,number(n,"intlAtmpHeatLngt"),(long)number(n,"walkCnt")));}return out;}
 private Map<String,IntersectionRow> intersections(List<JsonNode> pages){Map<String,IntersectionRow> out=new LinkedHashMap<>();pages.forEach(x->out.putAll(intersections(x)));return out;}
 private List<LinkRow> links(){
  JsonNode first=get(LINK_URL,Map.of("pageNo","1","numOfRows","1000"));int pages=(first.path("content").path("totalCount").asInt()+999)/1000;
  List<JsonNode> responses=new ArrayList<>();responses.add(first);
  if(pages>1)responses.addAll(java.util.stream.IntStream.rangeClosed(2,pages).mapToObj(page->CompletableFuture.supplyAsync(()->get(LINK_URL,Map.of("pageNo",String.valueOf(page),"numOfRows","1000")))).toList().stream().map(CompletableFuture::join).toList());
  List<LinkRow> out=new ArrayList<>();responses.forEach(root->{JsonNode items=root.path("content").path("items");if(items.isArray())items.forEach(n->out.add(new LinkRow(text(n,"lkId"),text(n,"roadNm"),text(n,"bgngNodeNm"),text(n,"endNodeNm"),number(n,"spd"),number(n,"vol"))));});return out;
 }
 private List<JsonNode> intersectionPages(String hour){JsonNode first=intersection(hour,1),content=first.path("content");int pages=(content.path("totalCount").asInt()+99)/100;List<JsonNode> out=new ArrayList<>();out.add(first);if(pages>1)out.addAll(java.util.stream.IntStream.rangeClosed(2,pages).mapToObj(page->CompletableFuture.supplyAsync(()->intersection(hour,page))).toList().stream().map(CompletableFuture::join).toList());return out;}
 private JsonNode intersection(String hour,int page){return get(INTERSECTION_URL,Map.of("pageNo",String.valueOf(page),"numOfRows","100","yyyyMMdd",hour.substring(0,8),"hour",hour.substring(8)));}
 private JsonNode get(String base,Map<String,String> params){var b=UriComponentsBuilder.fromUriString(base).queryParam("serviceKey",publicKey);params.forEach(b::queryParam);URI uri=b.build().encode().toUri();return apiCache.computeIfAbsent(uri.toString(),key->{JsonNode body=http.get().uri(uri).retrieve().body(JsonNode.class);String code=body==null?"":body.path("resultCode").asText();if(body==null||!("00".equals(code)||"0".equals(code))){log.warn("부산 교통정보 비정상 응답({}): code={}, message={}",Path.of(uri.getPath()).getFileName(),code,body==null?"응답 없음":body.path("resultMsg").asText());throw new TrafficDataException("부산 교통정보 응답이 올바르지 않습니다.");}return body;});}
 private TrafficSummary nationalFallback(String city,String district,double longitude,double latitude){
  if(itsKey.isBlank())throw new TrafficDataException("부산 교통 API 키가 설정되지 않았습니다.");
  double[] bounds=spatial.boundary(city,district).bounds();
  URI uri=UriComponentsBuilder.fromUriString("https://openapi.its.go.kr:9443/trafficInfo").queryParam("apiKey",itsKey).queryParam("type","all").queryParam("getType","json").queryParam("minX",bounds[0]).queryParam("maxX",bounds[2]).queryParam("minY",bounds[1]).queryParam("maxY",bounds[3]).build().encode().toUri();
  try{JsonNode body=http.get().uri(uri).retrieve().body(JsonNode.class);List<RoadSpeed> raw=new ArrayList<>();collect(body,raw);Map<String,List<List<Double>>> shapes=geometry.coordinates(raw.stream().map(RoadSpeed::linkId).collect(java.util.stream.Collectors.toSet()));List<RoadSpeed> roads=raw.stream().filter(x->x.speedKmh()>=10&&x.speedKmh()<=120).filter(x->inDistrict(shapes.get(x.linkId()),city,district,longitude,latitude)).map(x->new RoadSpeed(x.linkId(),x.roadName(),x.speedKmh(),x.volume(),x.intersectionName(),x.queueLength(),x.pedestrianCount(),shapes.get(x.linkId()))).toList();double avg=roads.stream().mapToDouble(RoadSpeed::speedKmh).average().orElse(0);List<IntersectionRow> observed=locatedIntersections();List<RoadSpeed> slow=roads.stream().filter(x->x.speedKmh()>=10&&x.speedKmh()<30).sorted(Comparator.comparingDouble(RoadSpeed::speedKmh)).map(x->withIntersection(x,observed)).toList();long matched=slow.stream().filter(x->x.intersectionName()!=null).count();return new TrafficSummary(city,district,roads.size(),round(avg),slow.size(),slow.stream().limit(20).toList(),matched>0?List.of("국가 ITS 실측 속도·도로 형상과 부산 스마트교차로 관측값을 노드 좌표로 결합했습니다.","10km/h 미만 속도값은 정지·대기·결측성 관측 가능성이 있어 혼잡 후보에서 제외했습니다."):List.of("국가 ITS 실측 속도와 국가표준 도로 형상을 구 경계 안에서 결합했습니다.","10km/h 미만 속도값은 정지·대기·결측성 관측 가능성이 있어 혼잡 후보에서 제외했습니다.","이름·좌표가 확실히 일치하는 스마트교차로가 없어 관측값을 임의 결합하지 않았습니다."));}catch(Exception e){throw new TrafficDataException("교통정보 API 호출에 실패했습니다.");}
 }
 private List<IntersectionRow> locatedIntersections(){if(publicKey.isBlank())return List.of();try{List<JsonNode> pages=List.of();LocalDateTime time=LocalDateTime.now();for(int i=0;i<4;i++){String hour=time.minusHours(i).format(DateTimeFormatter.ofPattern("yyyyMMddHH"));pages=intersectionPages(hour);if(pages.getFirst().path("content").path("totalCount").asInt()>0)break;}Map<String,List<RoadGeometryService.NamedNode>> nodes=geometry.namedNodes().stream().collect(java.util.stream.Collectors.groupingBy(x->intersectionKey(x.name())));Map<String,List<IntersectionRow>> rows=intersections(pages).values().stream().collect(java.util.stream.Collectors.groupingBy(x->intersectionKey(x.name())));List<IntersectionRow> out=new ArrayList<>();rows.forEach((key,values)->{List<RoadGeometryService.NamedNode> points=nodes.get(key);if(points==null||points.isEmpty())return;double lon=points.stream().mapToDouble(RoadGeometryService.NamedNode::longitude).average().orElse(0),lat=points.stream().mapToDouble(RoadGeometryService.NamedNode::latitude).average().orElse(0);out.add(new IntersectionRow(values.getFirst().name(),values.stream().mapToDouble(IntersectionRow::volume).sum(),values.stream().mapToDouble(IntersectionRow::queue).max().orElse(0),values.stream().mapToLong(IntersectionRow::walk).sum(),lon,lat));});return out;}catch(Exception e){log.warn("스마트교차로 좌표 결합 실패",e);return List.of();}}
 private RoadSpeed withIntersection(RoadSpeed road,List<IntersectionRow> observed){IntersectionRow match=observed.stream().min(Comparator.comparingDouble(x->distanceSquared(road.coordinates(),x.longitude(),x.latitude()))).filter(x->distanceSquared(road.coordinates(),x.longitude(),x.latitude())<=.000004).orElse(null);return match==null?road:new RoadSpeed(road.linkId(),road.roadName(),road.speedKmh(),round(match.volume()),match.name(),round(match.queue()),match.walk(),road.coordinates());}
 static double distanceSquared(List<List<Double>> line,double lon,double lat){return line.stream().filter(x->x.size()>1).mapToDouble(x->Math.pow(x.get(0)-lon,2)+Math.pow(x.get(1)-lat,2)).min().orElse(Double.MAX_VALUE);}
 private void collect(JsonNode n,List<RoadSpeed> out){if(n==null)return;if(n.isArray()){n.forEach(x->collect(x,out));return;}if(!n.isObject())return;String id=text(n,"linkId","linkid","linkNo"),s=text(n,"speed","spd");if(id!=null&&s!=null)try{out.add(new RoadSpeed(id,blank(text(n,"roadName","roadname","roadNm"),"도로명 없음"),Double.parseDouble(s),0,null,null,null,List.of()));}catch(NumberFormatException ignored){}n.properties().forEach(x->collect(x.getValue(),out));}
 static boolean inArea(List<List<Double>> c,double lon,double lat){return c!=null&&c.stream().anyMatch(p->p.size()>1&&Math.abs(p.get(0)-lon)<.08&&Math.abs(p.get(1)-lat)<.06);}
 private boolean inDistrict(List<List<Double>> coordinates,String city,String district,double lon,double lat){return spatial==null?inArea(coordinates,lon,lat):coordinates!=null&&coordinates.stream().anyMatch(p->p.size()>1&&spatial.contains(city,district,p.get(0),p.get(1)));}
 private boolean same(String a,String b){String x=intersectionKey(a),y=intersectionKey(b);return x.length()>1&&y.length()>1&&(x.contains(y)||y.contains(x));}
 private String intersectionKey(String value){return value==null?"":value.replaceAll("[^0-9A-Za-z가-힣]","").replaceFirst("(교차로|사거리|삼거리|로터리)$","");}
 private String text(JsonNode n,String...names){for(String name:names){JsonNode v=n.get(name);if(v!=null&&!v.isNull()&&!v.asText().isBlank())return v.asText();}return null;}
 private double number(JsonNode n,String name){JsonNode v=n.get(name);return v==null||v.isNull()?0:v.asDouble();}
 private String blank(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
 private double round(double x){return Math.round(x*10)/10d;}
 private record LinkRow(String id,String road,String start,String end,double speed,double volume){}
 private record IntersectionRow(String name,double volume,double queue,long walk,double longitude,double latitude){IntersectionRow(String name,double volume,double queue,long walk){this(name,volume,queue,walk,0,0);}}
 public record RoadSpeed(String linkId,String roadName,double speedKmh,double volume,String intersectionName,Double queueLength,Long pedestrianCount,List<List<Double>> coordinates){}
 public record TrafficSummary(String cityName,String districtName,int measuredLinkCount,double averageSpeedKmh,int congestedLinkCount,List<RoadSpeed> congestedRoads,List<String>warnings){}
}
