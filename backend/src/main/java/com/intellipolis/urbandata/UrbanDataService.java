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
 private static final List<String> BUSAN_DISTRICTS=List.of("강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구");
 private final Path raw; private final SpatialDataService spatial;private final ExternalDataService external;private final Map<String,DistrictSummary> cache=new ConcurrentHashMap<>();
 private volatile Map<String,List<String>> regionCache; private volatile Map<String,Long> businessCountCache=Map.of();
 private volatile boolean businessCountLoaded,businessCountAvailable;
 private volatile Map<String,LocalSummary> localSummaryCache; private volatile List<double[]> busStopCache;
 @Autowired public UrbanDataService(@Value("${app.urban-data.raw-path:data/raw}") String rawPath,SpatialDataService spatial,ExternalDataService external){raw=Path.of(rawPath);this.spatial=spatial;this.external=external;}
 public UrbanDataService(String rawPath){raw=Path.of(rawPath);spatial=null;external=null;}

 public synchronized Map<String,List<String>> regions(){
  if(regionCache==null)regionCache=Map.of("부산광역시",BUSAN_DISTRICTS);return regionCache;
 }

 public DistrictSummary summary(String city,String district){if(!regions().getOrDefault(city,List.of()).contains(district))throw new IllegalArgumentException("지원하지 않는 시·군·구입니다.");return cache.computeIfAbsent(city+"/"+district,k->load(city,district));}

 private DistrictSummary load(String city,String district){
  List<String>warnings=new ArrayList<>();Long busStops=null,hospitals=null;Double hospitalDistance=null;LocalSummary local;
  Map<String,Long> businesses=businessCounts();
  try{local=localSummaries().getOrDefault(city+"/"+district,new LocalSummary());}catch(RuntimeException e){local=new LocalSummary();warnings.add("지역 통계 파일 일부를 읽지 못해 해당 항목을 미수집으로 처리했습니다.");}
  if(find("전국초중등학교위치표준데이터")==null)warnings.add("전국 초중등학교 파일이 없어 학교 수를 집계하지 않았습니다.");
  if(find("202606_시군구_연령별인구현황")==null||find("202606_시군구_고령인구현황")==null)warnings.add("시군구 인구 파일이 없어 인구 비율을 집계하지 않았습니다.");
  if(!businessCountAvailable)warnings.add("상권 파일을 읽지 못해 상가 수를 집계하지 않았습니다.");
  try{if(spatial!=null)busStops=busStops().stream().filter(x->spatial.contains(city,district,x[0],x[1])).count();}catch(Exception e){warnings.add("버스정류장 공간 집계에 실패했습니다.");}
  try{if(external!=null){var data=external.hospitalData(city,district);hospitals=(long)data.count();if(spatial!=null&&!data.locations().isEmpty()){var candidates=spatial.candidates(city,district).candidates();hospitalDistance=averageHospitalDistance(candidates,data.locations());warnings.add("병원 Open API 좌표 "+data.locations().size()+"개로 생활권 후보의 평균 직선 접근거리를 계산했습니다.");}if(data.locations().size()<data.count())warnings.add("좌표가 확인된 병원 "+data.locations().size()+"개만 의료 접근거리 계산에 사용했습니다.");}}catch(Exception e){warnings.add("병원 Open API 집계에 실패했습니다: "+e.getMessage());}
  double elderlyRatio=local.population==0?0:local.elderly*100d/local.population,youthRatio=local.population==0?0:local.youth*100d/local.population;
  return new DistrictSummary(city,district,businesses.getOrDefault(city+"/"+district,0L),local.parks,local.parkArea,local.schoolAvailable?local.schools:null,null,busStops,hospitals,hospitalDistance,local.population,elderlyRatio,youthRatio,Map.copyOf(local.budget),warnings);
 }

 private double averageHospitalDistance(List<SpatialDataService.ParcelCandidate> candidates,List<ExternalDataService.Location> hospitals){if(candidates.isEmpty()||hospitals.isEmpty())return 0;double value=candidates.stream().mapToDouble(c->hospitals.stream().mapToDouble(h->Math.hypot((c.longitude()-h.longitude())*88,(c.latitude()-h.latitude())*111)).min().orElse(0)).average().orElse(0);return Math.round(value*100)/100d;}

 private synchronized Map<String,Long> businessCounts(){
  if(businessCountLoaded)return businessCountCache;businessCountLoaded=true;Map<String,Long> counts=new HashMap<>();Path file=find("소상공인시장진흥공단_상가(상권)정보_부산_"),cached=raw.resolveSibling("cache/urban/business-counts.properties");
  try{if(file!=null&&Files.exists(cached)&&Files.getLastModifiedTime(cached).compareTo(Files.getLastModifiedTime(file))>=0){Properties p=new Properties();try(Reader r=Files.newBufferedReader(cached,StandardCharsets.UTF_8)){p.load(r);}p.forEach((k,v)->counts.put(k.toString(),Long.parseLong(v.toString())));}else if(file!=null){scan(file,StandardCharsets.UTF_8,x->{if("부산광역시".equals(x.get("시도명")))counts.merge("부산광역시/"+x.get("시군구명"),1L,Long::sum);});Files.createDirectories(cached.getParent());Properties p=new Properties();counts.forEach((k,v)->p.setProperty(k,v.toString()));try(Writer w=Files.newBufferedWriter(cached,StandardCharsets.UTF_8)){p.store(w,"IntelliPolis business counts");}}businessCountCache=Map.copyOf(counts);businessCountAvailable=file!=null;}catch(Exception ignored){businessCountCache=Map.of();}
  return businessCountCache;
 }

 private synchronized Map<String,LocalSummary> localSummaries(){
  if(localSummaryCache!=null)return localSummaryCache;regions();Map<String,LocalSummary> data=new HashMap<>();regionCache.forEach((city,districts)->districts.forEach(d->data.put(city+"/"+d,new LocalSummary())));
  Path park=find("전국도시공원정보표준데이터");if(park!=null)scan(park,Charset.forName("MS949"),x->{String address=x.get("소재지도로명주소")+" "+x.get("소재지지번주소");matching(data,address).forEach(v->{v.parks++;v.parkArea+=number(x.get("공원면적"));});});
  Path finance=find("기능별 회계별 세출예산");if(finance!=null)scan(finance,StandardCharsets.UTF_8,x->{String org=x.get("자치단체명");data.forEach((key,v)->{String[] parts=key.split("/",2);if(org.equals(shortCity(parts[0])+parts[1]))v.budget.merge(x.get("분야명"),(long)number(x.get("세출예산순계액")),Long::sum);});});
  Path school=find("전국초중등학교위치표준데이터");if(school!=null){data.values().forEach(x->x.schoolAvailable=true);scan(school,Charset.forName("MS949"),x->{if("운영".equals(x.get("운영상태"))){String address=x.get("소재지도로명주소")+" "+x.get("소재지지번주소");matching(data,address).forEach(v->v.schools++);}});}
  Path age=find("202606_시군구_연령별인구현황");if(age!=null)scan(age,Charset.forName("MS949"),x->{LocalSummary v=data.get(region(x).replaceFirst(" ","/"));if(v!=null){v.population=(long)number(value(x,"_계_총인구수"));v.youth=(long)number(value(x,"_계_20~29세"));}});
  Path old=find("202606_시군구_고령인구현황");if(old!=null)scan(old,Charset.forName("MS949"),x->{LocalSummary v=data.get(region(x).replaceFirst(" ","/"));if(v!=null){if(v.population==0)v.population=(long)number(value(x,"_전체"));v.elderly=(long)number(value(x,"_65세이상전체"));}});
  localSummaryCache=Map.copyOf(data);return localSummaryCache;
 }
 private List<LocalSummary> matching(Map<String,LocalSummary> data,String address){return data.entrySet().stream().filter(x->address.contains(x.getKey().replace("/"," "))).map(Map.Entry::getValue).toList();}
 private synchronized List<double[]> busStops(){if(busStopCache!=null)return busStopCache;List<double[]> result=new ArrayList<>();Path bus=find("국토교통부_전국 버스정류장 위치정보_");if(bus!=null)scan(bus,Charset.forName("MS949"),x->{if("부산광역시".equals(x.get("도시명"))){double lat=number(x.get("위도")),lon=number(x.get("경도"));if(lon!=0&&lat!=0)result.add(new double[]{lon,lat});}});busStopCache=List.copyOf(result);return busStopCache;}
 private static final class LocalSummary{long parks,schools,population,elderly,youth;double parkArea;boolean schoolAvailable;final Map<String,Long> budget=new TreeMap<>();}

 private void scan(Path file,Charset charset,Consumer<CSVRecord> consumer){try(Reader reader=Files.newBufferedReader(file,charset);CSVParser parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)){for(CSVRecord record:parser)consumer.accept(record);}catch(IOException|IllegalArgumentException e){throw new UrbanDataException(file.getFileName()+" 파일을 읽을 수 없습니다.");}}
 private Path find(String prefix){try(var files=Files.list(raw)){return files.filter(Files::isRegularFile).filter(x->x.getFileName().toString().startsWith(prefix)).findFirst().orElse(null);}catch(IOException e){return null;}}
 private double number(String value){try{return value==null||value.isBlank()?0:Double.parseDouble(value.replace(",",""));}catch(NumberFormatException e){return 0;}}
 private String value(CSVRecord record,String suffix){return record.toMap().entrySet().stream().filter(x->x.getKey().endsWith(suffix)).map(Map.Entry::getValue).findFirst().orElse("");}
 private String region(CSVRecord record){return record.get("행정구역").replaceFirst("\\s*\\(\\d+\\)\\s*$","").trim();}
 private String shortCity(String city){return city.replace("특별시","").replace("광역시","");}

 public record DistrictSummary(String cityName,String districtName,long businessCount,long parkCount,double totalParkAreaM2,Long schoolCount,Long studentCount,Long busStopCount,Long hospitalCount,Double averageHospitalDistanceKm,long population,double elderlyRatio,double youthRatio,Map<String,Long> budgetByCategory,List<String> warnings){}
}
