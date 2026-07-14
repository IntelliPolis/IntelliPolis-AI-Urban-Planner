package com.intellipolis.traffic;

import com.intellipolis.common.config.ExternalHttp;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
public class TrafficDataService {
 private final String apiKey; private final RestClient http=ExternalHttp.create();
 public TrafficDataService(@Value("${app.its.api-key:}")String apiKey){this.apiKey=apiKey;}
 public Map<String,Object> status(){return Map.of("configured",!apiKey.isBlank(),"provider","ITS 국가교통정보센터");}
 public TrafficSummary summary(String city,String district,double longitude,double latitude){
  if(apiKey.isBlank())throw new TrafficDataException("ITS API 키가 설정되지 않았습니다.");
  URI uri=UriComponentsBuilder.fromUriString("https://openapi.its.go.kr:9443/trafficInfo")
   .queryParam("apiKey",apiKey).queryParam("type","all").queryParam("getType","json")
   .queryParam("minX",longitude-.06).queryParam("maxX",longitude+.06)
   .queryParam("minY",latitude-.05).queryParam("maxY",latitude+.05).build().encode().toUri();
  try{JsonNode body=http.get().uri(uri).retrieve().body(JsonNode.class);if(body==null)throw new TrafficDataException("교통소통 응답이 비어 있습니다.");return normalize(city,district,body);}
  catch(TrafficDataException e){throw e;}catch(Exception e){throw new TrafficDataException("ITS 교통소통정보 호출에 실패했습니다.");}
 }
 private TrafficSummary normalize(String city,String district,JsonNode root){
  List<RoadSpeed> roads=new ArrayList<>();collect(root,roads);var unique=new LinkedHashMap<String,RoadSpeed>();
  for(var road:roads)unique.putIfAbsent(road.linkId(),road);var values=new ArrayList<>(unique.values());
  double average=values.stream().mapToInt(RoadSpeed::speedKmh).average().orElse(0);long congestedCount=values.stream().filter(x->x.speedKmh()<20).count();var congested=values.stream().filter(x->x.speedKmh()<20).sorted(Comparator.comparingInt(RoadSpeed::speedKmh)).limit(20).toList();
  return new TrafficSummary(city,district,values.size(),Math.round(average*10)/10d,(int)congestedCount,congested,values.isEmpty()?List.of("조회 범위에 ITS 속도 자료가 없습니다."):List.of("속도 20km/h 미만 링크를 혼잡 후보로 분류했습니다."));
 }
 private void collect(JsonNode node,List<RoadSpeed> out){
  if(node==null)return;if(node.isArray()){node.forEach(x->collect(x,out));return;}if(!node.isObject())return;
  String link=text(node,"linkId","linkid","linkNo"),speed=text(node,"speed","spd");
  if(link!=null&&speed!=null)try{out.add(new RoadSpeed(link,Optional.ofNullable(text(node,"roadName","roadname","roadNm")).orElse("도로명 없음"),Integer.parseInt(speed.replaceAll("[^0-9-]",""))));}catch(NumberFormatException ignored){}
  node.properties().forEach(x->collect(x.getValue(),out));
 }
 private String text(JsonNode n,String...names){for(String name:names){JsonNode v=n.get(name);if(v!=null&&!v.isNull()&&!v.asText().isBlank())return v.asText();}return null;}
 public record RoadSpeed(String linkId,String roadName,int speedKmh){}
 public record TrafficSummary(String cityName,String districtName,int measuredLinkCount,double averageSpeedKmh,int congestedLinkCount,List<RoadSpeed> congestedRoads,List<String>warnings){}
}
