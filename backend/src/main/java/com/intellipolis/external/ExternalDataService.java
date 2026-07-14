package com.intellipolis.external;
import com.intellipolis.common.config.ExternalHttp;
import java.net.URI;import java.util.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;import org.springframework.web.client.RestClient;import org.springframework.web.util.UriComponentsBuilder;import tools.jackson.databind.JsonNode;
@Service public class ExternalDataService {
 private final String publicKey,neisKey,seoulKey;private final RestClient http=ExternalHttp.create();
 public ExternalDataService(@Value("${app.public-data.api-key:}")String p,@Value("${app.neis.api-key:}")String n,@Value("${app.seoul-data.api-key:}")String s){publicKey=p;neisKey=n;seoulKey=s;}
 public Map<String,Boolean> status(){return Map.of("publicData",!publicKey.isBlank(),"neis",!neisKey.isBlank(),"seoul",!seoulKey.isBlank());}
 public JsonNode hospitals(String sidoCode,String districtCode){require(publicKey,"공공데이터포털");return get(UriComponentsBuilder.fromUriString("https://apis.data.go.kr/B551182/hospInfoServicev2/getHospBasisList").queryParam("serviceKey",publicKey).queryParam("pageNo",1).queryParam("numOfRows",1000).queryParam("sidoCd",sidoCode).queryParam("sgguCd",districtCode).queryParam("_type","json").build().encode().toUri());}
 public JsonNode air(String sido){require(publicKey,"공공데이터포털");return get(UriComponentsBuilder.fromUriString("https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty").queryParam("serviceKey",publicKey).queryParam("returnType","json").queryParam("numOfRows",100).queryParam("pageNo",1).queryParam("sidoName",sido).queryParam("ver","1.0").build().encode().toUri());}
 public JsonNode schools(String officeCode,String district){require(neisKey,"나이스");return get(UriComponentsBuilder.fromUriString("https://open.neis.go.kr/hub/schoolInfo").queryParam("KEY",neisKey).queryParam("Type","json").queryParam("pIndex",1).queryParam("pSize",1000).queryParam("ATPT_OFCDC_SC_CODE",officeCode).queryParam("LCTN_SC_NM",district).build().encode().toUri());}
 private JsonNode get(URI uri){try{JsonNode body=http.get().uri(uri).retrieve().body(JsonNode.class);if(body==null)throw new ExternalDataException("외부 데이터 응답이 비어 있습니다.");return body;}catch(ExternalDataException e){throw e;}catch(Exception e){throw new ExternalDataException("외부 공공데이터 서버 호출에 실패했습니다.");}}
 private void require(String key,String name){if(key.isBlank())throw new ExternalDataException(name+" API 키가 설정되지 않았습니다.");}
}
