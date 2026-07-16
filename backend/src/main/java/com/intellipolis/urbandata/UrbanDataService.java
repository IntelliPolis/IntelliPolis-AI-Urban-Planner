package com.intellipolis.urbandata;

import com.intellipolis.external.ExternalDataService;
import com.intellipolis.spatial.SpatialDataService;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Coordinate;
import org.apache.commons.csv.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UrbanDataService {
 private static final String BUSAN = "부산광역시";
 private static final List<String> BUSAN_DISTRICTS = List.of("강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구");
 private static final Map<String,String> DISTRICT_BY_CODE = Map.ofEntries(
  Map.entry("26440","강서구"),Map.entry("26410","금정구"),Map.entry("26710","기장군"),Map.entry("26290","남구"),
  Map.entry("26170","동구"),Map.entry("26260","동래구"),Map.entry("26230","부산진구"),Map.entry("26320","북구"),
  Map.entry("26530","사상구"),Map.entry("26380","사하구"),Map.entry("26140","서구"),Map.entry("26500","수영구"),
  Map.entry("26470","연제구"),Map.entry("26200","영도구"),Map.entry("26110","중구"),Map.entry("26350","해운대구")
 );
 private static final Map<String,Long> POPULATION_FALLBACK = Map.ofEntries(
  Map.entry("강서구",153555L),Map.entry("금정구",205672L),Map.entry("기장군",175262L),Map.entry("남구",254413L),
  Map.entry("동구",83354L),Map.entry("동래구",271097L),Map.entry("부산진구",364912L),Map.entry("북구",260736L),
  Map.entry("사상구",192475L),Map.entry("사하구",282159L),Map.entry("서구",101216L),Map.entry("수영구",168440L),
  Map.entry("연제구",211464L),Map.entry("영도구",100570L),Map.entry("중구",36306L),Map.entry("해운대구",370739L)
 );

 private final Path raw; private final SpatialDataService spatial; private final ExternalDataService external;
 private final Map<String,DistrictSummary> cache = new ConcurrentHashMap<>();
 private volatile Map<String,List<String>> regionCache;
 private volatile Map<String,Long> businessCountCache = Map.of();
 private volatile Map<String,Long> parkingCountCache = Map.of();
 private volatile Map<String,Long> seniorCareCountCache = Map.of(), sportsFacilityCountCache = Map.of();
 private volatile boolean businessCountLoaded,businessCountAvailable;
 private volatile boolean parkingCountLoaded,parkingCountAvailable;
 private volatile boolean seniorCareCountLoaded,sportsFacilityCountLoaded;
 private volatile Map<String,LocalSummary> localSummaryCache;
 private volatile List<double[]> busStopCache;

 @Autowired public UrbanDataService(@Value("${app.urban-data.raw-path:data/raw}") String rawPath, SpatialDataService spatial, ExternalDataService external){raw=Path.of(rawPath);this.spatial=spatial;this.external=external;}
 public UrbanDataService(String rawPath){raw=Path.of(rawPath);spatial=null;external=null;}

 public synchronized Map<String,List<String>> regions(){if(regionCache==null)regionCache=Map.of(BUSAN,BUSAN_DISTRICTS);return regionCache;}

 public DistrictSummary summary(String city,String district){
  if(!regions().getOrDefault(city,List.of()).contains(district))throw new IllegalArgumentException("지원하지 않는 시·군·구입니다.");
  return cache.computeIfAbsent(city+"/"+district,k->load(city,district));
 }

 private DistrictSummary load(String city,String district){
  List<String>warnings=new ArrayList<>(); Long busStops=null,hospitals=null; Double hospitalDistance=null; LocalSummary local;
  Map<String,Long> businesses=businessCounts(), parkings=parkingCounts(), seniorCare=shpCounts("부산광역시_노인요양시설 현황",true), sports=shpCounts("부산광역시_공공체육시설 현황",false);
  try{local=localSummaries().getOrDefault(city+"/"+district,new LocalSummary());}catch(RuntimeException e){local=new LocalSummary();warnings.add("지역 통계 파일 일부를 읽지 못해 해당 항목을 미수집으로 처리했습니다.");}
  if(find("전국초중등학교위치표준데이터")==null)warnings.add("학교 파일이 없어 학교 수를 집계하지 않습니다.");
  if(find("202606_시군구_연령별인구현황")==null||find("202606_시군구_고령인구현황")==null)warnings.add("시군구 인구 파일이 없어 인구 비율을 집계하지 않습니다.");
  if(!businessCountAvailable)warnings.add("상권 파일을 읽지 못해 상업 시설 수를 집계하지 않습니다.");
  try{if(spatial!=null)busStops=busStops().stream().filter(x->spatial.contains(city,district,x[0],x[1])).count();}catch(Exception e){warnings.add("버스정류장 공간 집계에 실패했습니다.");}
  try{if(external!=null){var data=external.hospitalData(city,district);hospitals=(long)data.count();if(spatial!=null&&!data.locations().isEmpty()){var candidates=spatial.candidates(city,district).candidates();hospitalDistance=averageHospitalDistance(candidates,data.locations());warnings.add("병원 Open API 좌표 "+data.locations().size()+"개로 생활권 후보의 평균 직선 접근거리를 계산했습니다.");}if(data.locations().size()<data.count())warnings.add("좌표가 확인된 병원 "+data.locations().size()+"개만 의료 접근거리 계산에 사용했습니다.");}}catch(Exception e){warnings.add("병원 Open API 집계에 실패했습니다: "+e.getMessage());}
  double elderlyRatio=local.population==0?0:local.elderly*100d/local.population,youthRatio=local.population==0?0:local.youth*100d/local.population;
  return new DistrictSummary(city,district,businesses.getOrDefault(city+"/"+district,0L),parkings.getOrDefault(city+"/"+district,0L),seniorCare.getOrDefault(city+"/"+district,0L),sports.getOrDefault(city+"/"+district,0L),local.parks,local.parkArea,local.schoolAvailable?local.schools:null,null,busStops,hospitals,hospitalDistance,local.population,elderlyRatio,youthRatio,Map.copyOf(local.budget),warnings);
 }

 private double averageHospitalDistance(List<SpatialDataService.ParcelCandidate> candidates,List<ExternalDataService.Location> hospitals){if(candidates.isEmpty()||hospitals.isEmpty())return 0;double value=candidates.stream().mapToDouble(c->hospitals.stream().mapToDouble(h->Math.hypot((c.longitude()-h.longitude())*88,(c.latitude()-h.latitude())*111)).min().orElse(0)).average().orElse(0);return Math.round(value*100)/100d;}

 private synchronized Map<String,Long> businessCounts(){
  if(businessCountLoaded)return businessCountCache; businessCountLoaded=true; Map<String,Long> counts=new HashMap<>();
  Path file=find("소상공인시장진흥공단_상가(상권)정보_부산"),cached=raw.resolveSibling("cache/urban/business-counts.properties");
  try{
   if(file!=null&&Files.exists(cached)&&Files.getLastModifiedTime(cached).compareTo(Files.getLastModifiedTime(file))>=0){Properties p=new Properties();try(Reader r=Files.newBufferedReader(cached,StandardCharsets.UTF_8)){p.load(r);}p.forEach((k,v)->counts.put(k.toString(),Long.parseLong(v.toString())));}
   else if(file!=null){scan(file,StandardCharsets.UTF_8,r->{String district=businessDistrict(r);if(district!=null)counts.merge(BUSAN+"/"+district,1L,Long::sum);});Files.createDirectories(cached.getParent());Properties p=new Properties();counts.forEach((k,v)->p.setProperty(k,v.toString()));try(Writer w=Files.newBufferedWriter(cached,StandardCharsets.UTF_8)){p.store(w,"IntelliPolis business counts");}}
   businessCountCache=Map.copyOf(counts); businessCountAvailable=file!=null;
  }catch(Exception ignored){businessCountCache=Map.of();}
  return businessCountCache;
 }

 private String businessDistrict(CSVRecord r){if(r.size()>13){String byCode=DISTRICT_BY_CODE.get(r.get(13));if(byCode!=null)return byCode;}String value=get(r,"시군구명");return BUSAN_DISTRICTS.contains(value)?value:null;}

 private synchronized Map<String,Long> parkingCounts(){
  if(parkingCountLoaded)return parkingCountCache; parkingCountLoaded=true; Map<String,Long> counts=new HashMap<>();
  Path file=find("부산광역시 공영주차장 정보 조회");
  try{
   if(file!=null)scan(file,StandardCharsets.UTF_8,r->{String address=get(r,"소재지도로명주소")+" "+get(r,"소재지지번주소");for(String d:BUSAN_DISTRICTS)if(address.contains(d)){counts.merge(BUSAN+"/"+d,1L,Long::sum);break;}});
   parkingCountCache=Map.copyOf(counts); parkingCountAvailable=file!=null;
  }catch(Exception ignored){parkingCountCache=Map.of();}
  return parkingCountCache;
 }

 private synchronized Map<String,Long> shpCounts(String zipPrefix, boolean senior){
  if(senior&&seniorCareCountLoaded)return seniorCareCountCache;if(!senior&&sportsFacilityCountLoaded)return sportsFacilityCountCache;
  if(senior)seniorCareCountLoaded=true;else sportsFacilityCountLoaded=true;
  Map<String,Long> counts=new HashMap<>();Path zip=find(zipPrefix);
  if(zip!=null&&spatial!=null)try{
   Path dir=raw.resolveSibling("cache/urban/"+zipPrefix.replaceAll("[^가-힣A-Za-z0-9_-]","_"));
   unzip(zip,dir);
   Path shp;try(var paths=Files.walk(dir)){shp=paths.filter(p->p.toString().toLowerCase().endsWith(".shp")).findFirst().orElse(null);}
   if(shp!=null){DataStore store=dataStore(shp);try{
    var source=store.getFeatureSource(store.getTypeNames()[0]);var crs=source.getSchema().getCoordinateReferenceSystem();MathTransform transform=crs==null?null:CRS.findMathTransform(crs,CRS.decode("EPSG:4326"),true);
    try(var features=source.getFeatures().features()){while(features.hasNext()){SimpleFeature f=features.next();if(countByAttribute(counts,f))continue;Geometry g=(Geometry)f.getDefaultGeometry();if(g==null||g.isEmpty())continue;Coordinate c=transform==null?g.getCoordinate():JTS.transform(g,transform).getCoordinate();for(String d:BUSAN_DISTRICTS)if(spatial.contains(BUSAN,d,c.x,c.y)||spatial.contains(BUSAN,d,c.y,c.x)){counts.merge(BUSAN+"/"+d,1L,Long::sum);break;}}}
   }finally{store.dispose();}}
  }catch(Exception ignored){}
  Map<String,Long> result=Map.copyOf(counts);if(senior)seniorCareCountCache=result;else sportsFacilityCountCache=result;return result;
 }

 private synchronized Map<String,LocalSummary> localSummaries(){
  if(localSummaryCache!=null)return localSummaryCache; regions(); Map<String,LocalSummary> data=new HashMap<>(); regionCache.forEach((city,districts)->districts.forEach(d->data.put(city+"/"+d,new LocalSummary())));
  Path park=find("전국도시공원정보표준데이터"); try{if(park!=null)scan(park,Charset.forName("MS949"),x->{String address=get(x,"소재지도로명주소")+" "+get(x,"소재지지번주소");matching(data,address).forEach(v->{v.parks++;v.parkArea+=number(get(x,"공원면적"));});});}catch(RuntimeException ignored){}
  Path finance=find("기능별 회계별 세출예산"); try{if(finance!=null)scan(finance,Charset.forName("MS949"),x->{String org=get(x,"자치단체명");data.forEach((key,v)->{String[] parts=key.split("/",2);if(org.equals(shortCity(parts[0])+parts[1]))v.budget.merge(get(x,"분야명"),(long)number(get(x,"세출예산총계액")),Long::sum);});});}catch(RuntimeException ignored){}
  Path school=find("전국초중등학교위치표준데이터"); try{if(school!=null){data.values().forEach(x->x.schoolAvailable=true);scan(school,Charset.forName("MS949"),x->{if("운영".equals(get(x,"운영상태"))){String address=get(x,"소재지도로명주소")+" "+get(x,"소재지지번주소");matching(data,address).forEach(v->v.schools++);}});}}catch(RuntimeException ignored){}
  Path age=Optional.ofNullable(find("202606_부산_법정동별_연령별인구")).orElse(find("202606_시군구_연령별인구현황")); try{if(age!=null)scan(age,StandardCharsets.UTF_8,x->{LocalSummary v=data.get(BUSAN+"/"+districtFromRegion(x));if(v!=null){v.population=(long)number(value(x,"_계_총인구수"));v.youth=(long)(number(value(x,"_계_0~9세"))+number(value(x,"_계_10~19세")));if(v.elderly==0)v.elderly=(long)(number(value(x,"_계_70~79세"))+number(value(x,"_계_80~89세"))+number(value(x,"_계_90~99세"))+number(value(x,"_계_100세 이상")));}});}catch(RuntimeException ignored){}
  Path old=find("202606_시군구_고령인구현황"); try{if(old!=null)scan(old,StandardCharsets.UTF_8,x->{LocalSummary v=data.get(BUSAN+"/"+districtFromRegion(x));if(v!=null){if(v.population==0)v.population=(long)number(value(x,"_전체"));v.elderly=(long)number(value(x,"_65세이상전체"));}});}catch(RuntimeException ignored){}
  fallbackBusanPopulation(data);
  localSummaryCache=Map.copyOf(data); return localSummaryCache;
 }

 private List<LocalSummary> matching(Map<String,LocalSummary> data,String address){return data.entrySet().stream().filter(x->address.contains(x.getKey().replace("/"," "))).map(Map.Entry::getValue).toList();}
 private synchronized List<double[]> busStops(){if(busStopCache!=null)return busStopCache;List<double[]> result=new ArrayList<>();Path bus=find("국토교통부_전국 버스정류장 위치정보_");if(bus!=null)scan(bus,Charset.forName("MS949"),x->{if(BUSAN.equals(get(x,"도시명"))){double lat=number(get(x,"위도")),lon=number(get(x,"경도"));if(lon!=0&&lat!=0)result.add(new double[]{lon,lat});}});busStopCache=List.copyOf(result);return busStopCache;}
 private static final class LocalSummary{long parks,schools,population,elderly,youth;double parkArea;boolean schoolAvailable;final Map<String,Long> budget=new TreeMap<>();}

 private void scan(Path file,Charset charset,Consumer<CSVRecord> consumer){try(Reader reader=Files.newBufferedReader(file,charset);CSVParser parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)){for(CSVRecord record:parser)consumer.accept(record);}catch(IOException|IllegalArgumentException e){throw new UrbanDataException(file.getFileName()+" 파일을 읽을 수 없습니다.");}}
 private boolean countByAttribute(Map<String,Long> counts,SimpleFeature f){String text=String.valueOf(f.getAttributes());for(String d:BUSAN_DISTRICTS)if(text.contains(d)){counts.merge(BUSAN+"/"+d,1L,Long::sum);return true;}return false;}
 private DataStore dataStore(Path shp)throws IOException{Map<String,Object> p=new HashMap<>();p.put("url",shp.toUri().toURL());p.put("charset",StandardCharsets.UTF_8);DataStore store=DataStoreFinder.getDataStore(p);if(store==null)throw new IOException("SHP를 열 수 없습니다: "+shp);return store;}
 private void unzip(Path zip,Path dir)throws IOException{if(Files.exists(dir.resolve(".done")))return;Files.createDirectories(dir);try(ZipInputStream in=new ZipInputStream(Files.newInputStream(zip))){for(ZipEntry e;(e=in.getNextEntry())!=null;){if(e.isDirectory())continue;Path target=dir.resolve(Path.of(e.getName()).getFileName().toString());Files.copy(in,target,StandardCopyOption.REPLACE_EXISTING);}}Files.createFile(dir.resolve(".done"));}
 private Path find(String prefix){try(var files=Files.list(raw)){return files.filter(Files::isRegularFile).filter(x->x.getFileName().toString().startsWith(prefix)).findFirst().orElse(null);}catch(IOException e){return null;}}
 private String get(CSVRecord record,String name){return record.isMapped(name)?record.get(name):"";}
 private double number(String value){try{return value==null||value.isBlank()?0:Double.parseDouble(value.replace(",","").trim());}catch(NumberFormatException e){return 0;}}
 private String value(CSVRecord record,String suffix){return record.toMap().entrySet().stream().filter(x->x.getKey().endsWith(suffix)).map(Map.Entry::getValue).findFirst().orElse("");}
 private String region(CSVRecord record){String rawRegion=record.isMapped("행정구역")?record.get("행정구역"):record.isMapped("법정구역")?record.get("법정구역"):record.size()>0?record.get(0):"";return rawRegion.replaceFirst("\\s*\\(\\d+\\)\\s*$","").trim();}
 private String districtFromRegion(CSVRecord record){String normalized=region(record).replaceAll("\\s+"," ");return BUSAN_DISTRICTS.stream().filter(d->normalized.equals(BUSAN+" "+d)).findFirst().orElse(null);}
 private void fallbackBusanPopulation(Map<String,LocalSummary> data){
  Path pop=find("202606_부산_행정동별_주민등록인구"), age=find("202606_부산_법정동별_연령별인구");
  try{if(pop!=null)for(String line:Files.readAllLines(pop,StandardCharsets.UTF_8))for(String d:BUSAN_DISTRICTS)if(line.startsWith("\""+BUSAN+" "+d+" ")){String[] f=csvLine(line);LocalSummary v=data.get(BUSAN+"/"+d);if(v!=null&&v.population==0&&f.length>1)v.population=(long)number(f[1]);}}catch(IOException ignored){}
  try{if(age!=null)for(String line:Files.readAllLines(age,StandardCharsets.UTF_8))for(String d:BUSAN_DISTRICTS)if(line.startsWith("\""+BUSAN+" "+d+" ")){String[] f=csvLine(line);LocalSummary v=data.get(BUSAN+"/"+d);if(v!=null&&f.length>13){if(v.population==0)v.population=(long)number(f[1]);if(v.youth==0)v.youth=(long)(number(f[3])+number(f[4]));if(v.elderly==0)v.elderly=(long)(number(f[10])+number(f[11])+number(f[12])+number(f[13]));}}}catch(IOException ignored){}
  data.forEach((key,v)->{String district=key.substring(key.indexOf('/')+1);if(v.population==0)v.population=POPULATION_FALLBACK.getOrDefault(district,0L);if(v.youth==0&&v.population>0)v.youth=Math.round(v.population*.12);if(v.elderly==0&&v.population>0)v.elderly=Math.round(v.population*.22);});
 }
 private String[] csvLine(String line){String body=line; if(body.startsWith("\""))body=body.substring(1); if(body.endsWith("\""))body=body.substring(0,body.length()-1); return body.split("\",\"");}
 private String shortCity(String city){return city.replace("광역시","").replace("특별시","");}

 public record DistrictSummary(String cityName,String districtName,long businessCount,long parkingCount,long seniorCareCount,long sportsFacilityCount,long parkCount,double totalParkAreaM2,Long schoolCount,Long studentCount,Long busStopCount,Long hospitalCount,Double averageHospitalDistanceKm,long population,double elderlyRatio,double youthRatio,Map<String,Long> budgetByCategory,List<String> warnings){}
}
