package com.intellipolis.vworld;

import com.intellipolis.common.config.ExternalHttp;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class VWorldService {
 private final String apiKey; private final String domain; private final RestClient client=ExternalHttp.create();
 public VWorldService(@Value("${app.vworld.api-key:}") String apiKey,@Value("${app.vworld.domain:http://localhost:5173}") String domain){this.apiKey=apiKey;this.domain=domain;}
 public Map<String,Object> status(){return Map.of("configured",!apiKey.isBlank(),"domain",domain,"service","VWorld OpenAPI");}
 public byte[] tile(int z,int y,int x){
  if(apiKey.isBlank())throw new VWorldException("브이월드 API 키가 설정되지 않았습니다.");
  try{return client.get().uri("https://api.vworld.kr/req/wmts/1.0.0/{key}/Base/{z}/{y}/{x}.png",apiKey,z,y,x).retrieve().body(byte[].class);}
  catch(Exception e){throw new VWorldException("브이월드 배경지도를 불러오지 못했습니다.");}
 }
}
