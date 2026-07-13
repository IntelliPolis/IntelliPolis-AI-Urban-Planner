package com.intellipolis.vworld;

import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
public class VWorldService {
 private final String apiKey; private final String domain; private final RestClient client=RestClient.create();
 public VWorldService(@Value("${app.vworld.api-key:}") String apiKey,@Value("${app.vworld.domain:http://localhost:5173}") String domain){this.apiKey=apiKey;this.domain=domain;}
 public Map<String,Object> status(){return Map.of("configured",!apiKey.isBlank(),"domain",domain,"service","VWorld OpenAPI");}
 public byte[] tile(int z,int y,int x){
  if(apiKey.isBlank())throw new VWorldException("브이월드 API 키가 설정되지 않았습니다.");
  try{return client.get().uri("https://api.vworld.kr/req/wmts/1.0.0/{key}/Base/{z}/{y}/{x}.png",apiKey,z,y,x).retrieve().body(byte[].class);}
  catch(Exception e){throw new VWorldException("브이월드 배경지도를 불러오지 못했습니다.");}
 }
 public JsonNode search(String query){
  if(apiKey.isBlank())throw new VWorldException("브이월드 API 키가 설정되지 않았습니다.");
  URI uri=UriComponentsBuilder.fromUriString("https://api.vworld.kr/req/search")
   .queryParam("service","search").queryParam("request","search").queryParam("version","2.0")
   .queryParam("query",query).queryParam("type","district").queryParam("category","L2")
   .queryParam("format","json").queryParam("size",20).queryParam("page",1)
   .queryParam("key",apiKey).queryParam("domain",domain).build().encode().toUri();
  try {
   JsonNode body=client.get().uri(uri).retrieve().body(JsonNode.class);
   if(body==null||!"OK".equals(body.path("response").path("status").asText()))throw new VWorldException("브이월드에서 지역 검색 결과를 가져오지 못했습니다.");
   return body;
  } catch(VWorldException e){throw e;} catch(Exception e){throw new VWorldException("브이월드 서버에 연결할 수 없습니다.");}
 }
}
