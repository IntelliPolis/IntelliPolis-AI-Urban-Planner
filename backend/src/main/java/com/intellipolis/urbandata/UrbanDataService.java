package com.intellipolis.urbandata;

import com.intellipolis.external.ExternalDataService;
import com.intellipolis.spatial.SpatialDataService;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.apache.commons.csv.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UrbanDataService {
 private static final List<String> CITIES=List.of("서울특별시","부산광역시","인천광역시","대구광역시","대전광역시","광주광역시","울산광역시");
 private final Path raw; private final SpatialDataService spatial;private final ExternalDataService external;private final Map<String,DistrictSummary> cache=new ConcurrentHashMap<>();
 private volatile Map<String,List<String>> regionCache; private volatile Map<String,Long> businessCountCache=Map.of();
 @Autowired public UrbanDataService(@Value("${app.urban-data.raw-path:data/raw}") String rawPath,SpatialDataService spatial,ExternalDataService external){raw=Path.of(rawPath);this.spatial=spatial;this.external=external;}
 public UrbanDataService(String rawPath){raw=Path.of(rawPath);spatial=null;external=null;}

 public synchronized Map<String,List<String>> regions(){
  if(regionCache!=null)return regionCache;Map<String,Set<String>> found=new LinkedHashMap<>();Map<String,Long> counts=new HashMap<>();CITIES.forEach(c->found.put(c,new TreeSet<>()));
  for(String city:CITIES){Path file=find("소상공인시장진흥공단_상가(상권)정보_"+shortCity(city)+"_");if(file!=null)scan(file,StandardCharsets.UTF_8,x->{if(city.equals(x.get("시도명"))){String district=x.get("시군구명");found.get(city).add(district);counts.merge(city+"/"+district,1L,Long::sum);}});}
  Map<String,List<String>> result=new LinkedHashMap<>();found.forEach((city,districts)->result.put(city,List.copyOf(districts)));businessCountCache=Map.copyOf(counts);regionCache=Collections.unmodifiableMap(result);return regionCache;
 }

 public DistrictSummary summary(String city,String district){if(!regions().getOrDefault(city,List.of()).contains(district))throw new IllegalArgumentException("지원하지 않는 시·군·구입니다.");return cache.computeIfAbsent(city+"/"+district,k->load(city,district));}

 private DistrictSummary load(String city,String district){
  long businesses=0,parks=0;double parkArea=0;Map<String,Long> budget=new TreeMap<>();
  businesses=businessCountCache.getOrDefault(city+"/"+district,0L);
  Path park=find("전국도시공원정보표준데이터");
  long[] parkCount={0};double[] parkAreaSum={0};if(park!=null)scan(park,Charset.forName("MS949"),x->{String address=x.get("소재지도로명주소")+" "+x.get("소재지지번주소");if(address.contains(city+" "+district)){parkCount[0]++;parkAreaSum[0]+=number(x.get("공원면적"));}});parks=parkCount[0];parkArea=parkAreaSum[0];
  Path finance=find("기능별 회계별 세출예산");
  if(finance!=null)scan(finance,StandardCharsets.UTF_8,x->{String org=x.get("자치단체명");if(org.equals(shortCity(city)+district))budget.merge(x.get("분야명"),(long)number(x.get("세출예산순계액")),Long::sum);});
  long[] schools={0};Path school=find("전국초중등학교위치표준데이터");if(school!=null)scan(school,Charset.forName("MS949"),x->{String address=x.get("소재지도로명주소")+" "+x.get("소재지지번주소");if("운영".equals(x.get("운영상태"))&&address.contains(city+" "+district))schools[0]++;});
  long[] population={0},elderly={0},youth={0};Path age=find("202606_시군구_연령별인구현황");if(age!=null)scan(age,Charset.forName("MS949"),x->{if(region(x).equals(city+" "+district)){population[0]=(long)number(value(x,"_계_총인구수"));youth[0]=(long)number(value(x,"_계_20~29세"));}});Path old=find("202606_시군구_고령인구현황");if(old!=null)scan(old,Charset.forName("MS949"),x->{if(region(x).equals(city+" "+district)){if(population[0]==0)population[0]=(long)number(value(x,"_전체"));elderly[0]=(long)number(value(x,"_65세이상전체"));}});
  Long busStops=null,hospitals=null;List<String>warnings=new ArrayList<>();if(school==null)warnings.add("전국 초중등학교 파일이 없어 학교 수를 집계하지 않았습니다.");if(age==null||old==null)warnings.add("시군구 인구 파일이 없어 인구 비율을 집계하지 않았습니다.");try{if(spatial!=null){long[] count={0};if("서울특별시".equals(city)){for(double[] p:external.seoulBusStops())if(spatial.contains(city,district,p[0],p[1]))count[0]++;}else{Path bus=find("국토교통부_전국 버스정류장 위치정보_");if(bus!=null)scan(bus,Charset.forName("MS949"),x->{double lat=number(x.get("위도")),lon=number(x.get("경도"));if(lat!=0&&lon!=0&&spatial.contains(city,district,lon,lat))count[0]++;});}busStops=count[0];}}catch(Exception e){warnings.add("버스정류장 공간 집계에 실패했습니다.");}try{if(external!=null)hospitals=(long)external.hospitalCount(city,district);}catch(Exception e){warnings.add("병원 Open API 집계에 실패했습니다.");}
  double elderlyRatio=population[0]==0?0:elderly[0]*100d/population[0],youthRatio=population[0]==0?0:youth[0]*100d/population[0];
  return new DistrictSummary(city,district,businesses,parks,parkArea,school==null?null:schools[0],null,busStops,hospitals,population[0],elderlyRatio,youthRatio,budget,warnings);
 }

 private void scan(Path file,Charset charset,Consumer<CSVRecord> consumer){try(Reader reader=Files.newBufferedReader(file,charset);CSVParser parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)){for(CSVRecord record:parser)consumer.accept(record);}catch(IOException|IllegalArgumentException e){throw new UrbanDataException(file.getFileName()+" 파일을 읽을 수 없습니다.");}}
 private Path find(String prefix){try(var files=Files.list(raw)){return files.filter(Files::isRegularFile).filter(x->x.getFileName().toString().startsWith(prefix)).findFirst().orElse(null);}catch(IOException e){return null;}}
 private double number(String value){try{return value==null||value.isBlank()?0:Double.parseDouble(value.replace(",",""));}catch(NumberFormatException e){return 0;}}
 private String value(CSVRecord record,String suffix){return record.toMap().entrySet().stream().filter(x->x.getKey().endsWith(suffix)).map(Map.Entry::getValue).findFirst().orElse("");}
 private String region(CSVRecord record){return record.get("행정구역").replaceFirst("\\s*\\(\\d+\\)\\s*$","").trim();}
 private String shortCity(String city){return city.replace("특별시","").replace("광역시","");}

 public record DistrictSummary(String cityName,String districtName,long businessCount,long parkCount,double totalParkAreaM2,Long schoolCount,Long studentCount,Long busStopCount,Long hospitalCount,long population,double elderlyRatio,double youthRatio,Map<String,Long> budgetByCategory,List<String> warnings){}
}
