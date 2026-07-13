package com.intellipolis.urbandata;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.apache.commons.csv.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UrbanDataService {
 private static final Set<String> CITIES=Set.of("서울특별시","부산광역시","대구광역시","인천광역시","광주광역시","대전광역시","울산광역시");
 private final Path raw; private final Map<String,DistrictSummary> cache=new ConcurrentHashMap<>(); private volatile Map<String,List<String>> regionCache;
 public UrbanDataService(@Value("${app.urban-data.raw-path:data/raw}") String rawPath){raw=Path.of(rawPath);}

 public synchronized Map<String,List<String>> regions(){
  if(regionCache!=null)return regionCache;Map<String,Set<String>> found=new TreeMap<>();CITIES.forEach(c->found.put(c,new TreeSet<>()));
  for(String city:CITIES){Path file=find("소상공인시장진흥공단_상가(상권)정보_"+shortCity(city)+"_");if(file!=null)scan(file,StandardCharsets.UTF_8,x->{if(city.equals(x.get("시도명")))found.get(city).add(x.get("시군구명"));});}
  Map<String,List<String>> result=new LinkedHashMap<>();found.forEach((city,districts)->result.put(city,List.copyOf(districts)));regionCache=Collections.unmodifiableMap(result);return regionCache;
 }

 public DistrictSummary summary(String city,String district){if(!regions().getOrDefault(city,List.of()).contains(district))throw new IllegalArgumentException("지원하지 않는 시·군·구입니다.");return cache.computeIfAbsent(city+"/"+district,k->load(city,district));}

 private DistrictSummary load(String city,String district){
  long businesses=0,parks=0;double parkArea=0;Map<String,Long> budget=new TreeMap<>();
  Path business=find("소상공인시장진흥공단_상가(상권)정보_"+shortCity(city)+"_");
  long[] businessCount={0};if(business!=null)scan(business,StandardCharsets.UTF_8,x->{if(district.equals(x.get("시군구명")))businessCount[0]++;});businesses=businessCount[0];
  Path park=find("전국도시공원정보표준데이터");
  long[] parkCount={0};double[] parkAreaSum={0};if(park!=null)scan(park,Charset.forName("MS949"),x->{String address=x.get("소재지도로명주소")+" "+x.get("소재지지번주소");if(address.contains(city+" "+district)){parkCount[0]++;parkAreaSum[0]+=number(x.get("공원면적"));}});parks=parkCount[0];parkArea=parkAreaSum[0];
  Path finance=find("기능별 회계별 세출예산");
  if(finance!=null)scan(finance,StandardCharsets.UTF_8,x->{String org=x.get("자치단체명");if(org.equals(shortCity(city)+district))budget.merge(x.get("분야명"),(long)number(x.get("세출예산순계액")),Long::sum);});
  return new DistrictSummary(city,district,businesses,parks,parkArea,null,null,null,budget,List.of("학교 파일은 각종학교만 포함하므로 전체 학교 수로 사용하지 않습니다.","버스정류장 파일은 서울 누락 및 구 단위 식별 한계로 집계하지 않습니다.","병원 수와 인구는 승인된 Open API 연동 후 추가됩니다."));
 }

 private void scan(Path file,Charset charset,Consumer<CSVRecord> consumer){try(Reader reader=Files.newBufferedReader(file,charset);CSVParser parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)){for(CSVRecord record:parser)consumer.accept(record);}catch(IOException|IllegalArgumentException e){throw new UrbanDataException(file.getFileName()+" 파일을 읽을 수 없습니다.");}}
 private Path find(String prefix){try(var files=Files.list(raw)){return files.filter(Files::isRegularFile).filter(x->x.getFileName().toString().startsWith(prefix)).findFirst().orElse(null);}catch(IOException e){return null;}}
 private double number(String value){try{return value==null||value.isBlank()?0:Double.parseDouble(value.replace(",",""));}catch(NumberFormatException e){return 0;}}
 private String shortCity(String city){return city.replace("특별시","").replace("광역시","");}

 public record DistrictSummary(String cityName,String districtName,long businessCount,long parkCount,double totalParkAreaM2,Long schoolCount,Long studentCount,Long busStopCount,Map<String,Long> budgetByCategory,List<String> warnings){}
}
