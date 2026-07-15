package com.intellipolis.spatial;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.overlayng.UnaryUnionNG;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SpatialDataService {
    private static final double ACTIVITY_RADIUS_DEGREES = 0.009;
    private static final int MIN_ACTIVITY_POINTS = 3;
    private static final CoordinateReferenceSystem WGS84;
    private static final CoordinateReferenceSystem KOREA_2000;
    private static final Map<String, String> CADASTRAL = Map.of("부산광역시", "LSMD_CONT_LDREG_부산.zip");
    private static final Map<String, String> CITY_CODES = Map.of("부산광역시", "26000");
    static {
        try { WGS84 = CRS.decode("EPSG:4326", true); KOREA_2000 = CRS.decode("EPSG:5179", true); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private final Path raw;
    private final Path cache;
    private final Map<String, DistrictBoundary> boundaryCache = new ConcurrentHashMap<>();
    private final Map<String, Geometry> boundaryGeometryCache = new ConcurrentHashMap<>();
    private final Map<String, PreparedGeometry> preparedBoundaryCache = new ConcurrentHashMap<>();
    private final Map<CandidateKey, SpatialCandidates> candidateCache = new ConcurrentHashMap<>();
    public SpatialDataService(@Value("${app.urban-data.raw-path:data/raw}") String rawPath) {
        raw = Path.of(rawPath);
        cache = raw.resolveSibling("cache/spatial");
    }

    public SpatialCandidates candidates(String city, String district) {
        return candidateCache.computeIfAbsent(new CandidateKey(city, district),
                ignored -> loadCandidates(city, district));
    }

    private SpatialCandidates loadCandidates(String city, String district) {
        List<String> warnings = new ArrayList<>();
        try {
            String filename = CADASTRAL.get(city);
            if (filename == null) return new SpatialCandidates(city, district, 0, 0, 0, 0, 0, List.of(), List.of("부산광역시만 지원합니다."));
            Path candidateFile=cache.resolve(CITY_CODES.get(city)).resolve("candidates").resolve(districtPrefix(city,district)+"-v4.csv");
            if(Files.exists(candidateFile))return readCandidates(city,district,candidateFile);
            Path cadastral = cache.resolve(CITY_CODES.get(city)).resolve("cadastral");
            extractZip(raw.resolve("cadastral").resolve(filename), cadastral);
            boundary(city,district);Geometry districtGeometry=boundaryGeometryCache.get(city+"/"+district);
            List<Geometry> facilities = loadFacilities(city, districtGeometry, warnings);
            List<Geometry> zoning = loadZoning(districtGeometry);
            List<Geometry> activity = loadActivityPoints(district, districtGeometry);
            List<ParcelCandidate> parcels = loadParcels(cadastral, districtPrefix(city,district), districtGeometry, facilities, zoning, activity);
            double parkDistance=averageNearestKm(parcels,loadParkPoints(city,district,districtGeometry));
            double transitDistance=averageNearestKm(parcels,loadBusStopPoints(city,district,districtGeometry));
            warnings.add("구 전체에서 500㎡ 이상이며 도시계획시설과 겹치지 않는 필지를 분석했습니다.");
            warnings.add("국토계획/도시지역 전체데이터와 겹치는 필지만 후보로 사용했습니다.");
            warnings.add("2026년 6월 법정동 인구를 반영해 사람이 실제로 거주하는 생활권 후보를 우선했습니다.");
            SpatialCandidates result=new SpatialCandidates(city, district, parcels.size(), facilities.size(), zoning.size(), parkDistance, transitDistance, parcels.stream().limit(20).toList(), warnings);writeCandidates(candidateFile,result);return result;
        } catch (Exception e) {
            throw new SpatialDataException("공간데이터를 읽지 못했습니다.", e);
        }
    }

    public DistrictBoundary boundary(String city, String district) {
        return boundaryCache.computeIfAbsent(city + "/" + district, ignored -> loadBoundary(city, district));
    }

    public boolean contains(String city, String district, double longitude, double latitude) {
        String key = city + "/" + district;
        boundary(city, district);
        PreparedGeometry prepared=preparedBoundaryCache.computeIfAbsent(key,k->PreparedGeometryFactory.prepare(boundaryGeometryCache.get(k)));
        return prepared.covers(new GeometryFactory().createPoint(new Coordinate(longitude, latitude)));
    }

    private DistrictBoundary loadBoundary(String city, String district) {
        try {
            String filename = CADASTRAL.get(city);
            if (filename == null) throw new IOException("부산광역시만 지원합니다.");
            String prefix = districtPrefix(city,district);
            Path boundaryFile=cache.resolve(CITY_CODES.get(city)).resolve("boundaries").resolve(prefix+".wkb");
            if(Files.exists(boundaryFile))return boundary(city,district,new WKBReader().read(Files.readAllBytes(boundaryFile)));
            Path dir = cache.resolve(CITY_CODES.get(city)).resolve("cadastral");
            extractZip(raw.resolve("cadastral").resolve(filename), dir);
            DataStore store = dataStore(firstShapefile(dir));
            try {
                SimpleFeatureSource source = store.getFeatureSource(store.getTypeNames()[0]);
                List<Geometry> parcels = new ArrayList<>();
                try (var features = source.getFeatures().features()) {
                    while (features.hasNext()) {
                        SimpleFeature feature = features.next();
                        Object pnu = feature.getAttribute("PNU");
                        if (pnu == null || !pnu.toString().startsWith(prefix)) continue;
                        Geometry geometry = (Geometry) feature.getDefaultGeometry();
                        if (geometry != null && !geometry.isEmpty()) parcels.add(geometry);
                    }
                }
                if (parcels.isEmpty()) throw new IOException("구 경계에 사용할 필지가 없습니다.");
                Geometry merged = TopologyPreservingSimplifier.simplify(UnaryUnionNG.union(parcels,new PrecisionModel(1)), 15);
                Geometry wgs = JTS.transform(merged, CRS.findMathTransform(source.getSchema().getCoordinateReferenceSystem(), WGS84, true));
                Files.createDirectories(boundaryFile.getParent());Files.write(boundaryFile,new WKBWriter().write(wgs));
                return boundary(city,district,wgs);
            } finally { store.dispose(); }
        } catch (Exception e) { throw new SpatialDataException("구 경계를 만들지 못했습니다.", e); }
    }

    private String geometryType(Geometry geometry) { return geometry instanceof MultiPolygon ? "MultiPolygon" : "Polygon"; }

    private Object coordinates(Geometry geometry) {
        if (geometry instanceof MultiPolygon multi) {
            List<Object> polygons = new ArrayList<>();
            for (int i = 0; i < multi.getNumGeometries(); i++) polygons.add(polygonCoordinates((Polygon) multi.getGeometryN(i)));
            return polygons;
        }
        return polygonCoordinates((Polygon) geometry);
    }

    private List<Object> polygonCoordinates(Polygon polygon) {
        List<Object> rings = new ArrayList<>();
        rings.add(ringCoordinates(polygon.getExteriorRing().getCoordinates()));
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) rings.add(ringCoordinates(polygon.getInteriorRingN(i).getCoordinates()));
        return rings;
    }

    private List<double[]> ringCoordinates(Coordinate[] coordinates) {
        List<double[]> result = new ArrayList<>(coordinates.length);
        for (Coordinate coordinate : coordinates) result.add(new double[]{coordinate.x, coordinate.y});
        return result;
    }

    private String districtPrefix(String city,String district)throws IOException{return Files.readAllLines(raw.resolve("법정동코드 전체자료.txt"),Charset.forName("MS949")).stream().map(line->line.split("\t")).filter(row->row.length>=3&&row[1].equals(city+" "+district)).map(row->row[0].substring(0,5)).findFirst().orElseThrow(()->new IOException("법정동코드를 찾지 못했습니다."));}
    private SpatialCandidates readCandidates(String city,String district,Path file)throws IOException{List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);String[] count=lines.getFirst().split(",");List<ParcelCandidate> candidates=lines.stream().skip(1).map(x->x.split(",")).map(x->new ParcelCandidate(x[0],Double.parseDouble(x[1]),Double.parseDouble(x[2]),Long.parseLong(x[3]))).toList();return new SpatialCandidates(city,district,Integer.parseInt(count[0]),Integer.parseInt(count[1]),Integer.parseInt(count[2]),Double.parseDouble(count[3]),Double.parseDouble(count[4]),candidates,List.of("구 전체에서 검증하고 분산한 후보지 캐시를 사용했습니다.","국토계획/도시지역 전체데이터와 겹치는 필지만 후보로 사용했습니다.","2026년 6월 법정동 인구를 반영해 사람이 실제로 거주하는 생활권 후보를 우선했습니다."));}
    private void writeCandidates(Path file,SpatialCandidates value)throws IOException{Files.createDirectories(file.getParent());List<String> lines=new ArrayList<>();lines.add(value.eligibleParcelCount()+","+value.nearbyPlanningFacilityCount()+","+value.nearbyZoningPolygonCount()+","+value.averageParkDistanceKm()+","+value.averageTransitDistanceKm());value.candidates().forEach(x->lines.add(x.parcelId()+","+x.longitude()+","+x.latitude()+","+x.areaM2()));Files.write(file,lines,StandardCharsets.UTF_8);}

    private List<ParcelCandidate> loadParcels(Path dir, String prefix, Geometry district, List<Geometry> facilities, List<Geometry> zoning, List<Geometry> activity) throws Exception {
        List<ScoredCandidate> result = new ArrayList<>();
        Map<String,Long> population = legalDongPopulation();
        STRtree facilityIndex = spatialIndex(facilities);
        STRtree zoningIndex = spatialIndex(zoning);
        STRtree activityIndex = spatialIndex(activity);
        DataStore store = dataStore(firstShapefile(dir));
        try {
            SimpleFeatureSource source = store.getFeatureSource(store.getTypeNames()[0]);
            CoordinateReferenceSystem crs = source.getSchema().getCoordinateReferenceSystem();
            MathTransform fromWgs = CRS.findMathTransform(WGS84, crs, true);
            MathTransform toWgs = CRS.findMathTransform(crs, WGS84, true);
            Envelope box = JTS.transform(district, fromWgs).getEnvelopeInternal();
            PreparedGeometry districtArea=PreparedGeometryFactory.prepare(district);
            try (var features = source.getFeatures(query(source, box)).features()) {
                while (features.hasNext()) {
                    SimpleFeature feature = features.next();
                    Object pnu = feature.getAttribute("PNU");
                    if(pnu==null||!pnu.toString().startsWith(prefix))continue;
                    long residents=population.getOrDefault(pnu.toString().substring(0,10),0L);
                    if(!population.isEmpty()&&residents==0)continue;
                    Geometry geometry = (Geometry) feature.getDefaultGeometry();
                    if (geometry == null || geometry.isEmpty() || geometry.getArea() < 500) continue;
                    Geometry wgsGeometry = valid(JTS.transform(geometry, toWgs));
                    if(!districtArea.covers(wgsGeometry.getInteriorPoint()))continue;
                    if (intersectsAny(facilityIndex, wgsGeometry)) continue;
                    Geometry interiorPoint = wgsGeometry.getInteriorPoint();
                    if (!coveredByAny(zoningIndex, interiorPoint)) continue;
                    int activityCount = nearbyCount(activityIndex, interiorPoint, ACTIVITY_RADIUS_DEGREES);
                    if (activityCount < MIN_ACTIVITY_POINTS) continue;
                    Coordinate point = interiorPoint.getCoordinate();
                    result.add(new ScoredCandidate(new ParcelCandidate(pnu.toString(), point.x, point.y,
                            Math.round(geometry.getArea())), activityCount, residents));
                }
            }
        } finally { store.dispose(); }
        result.sort(Comparator.comparingDouble(SpatialDataService::demandScore).reversed());
        return diverse(result);
    }

    private Map<String,Long> legalDongPopulation() throws IOException {
        Path file=findRawFile("202606_부산_법정동별_연령별인구");
        Map<String,Long> result=new HashMap<>();
        try(Reader reader=Files.newBufferedReader(file,Charset.forName("MS949"));CSVParser parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)){
            for(CSVRecord row:parser){
                var code=java.util.regex.Pattern.compile("\\((26\\d{8})\\)").matcher(row.get("법정구역"));
                if(!code.find())continue;
                String value=row.toMap().entrySet().stream().filter(x->x.getKey().endsWith("_계_총인구수")).map(Map.Entry::getValue).findFirst().orElse("0");
                result.put(code.group(1),Long.parseLong(value.replace(",","")));
            }
        }
        return result;
    }

    static double demandScore(ScoredCandidate candidate){return candidate.activityCount()+Math.log1p(candidate.population())*2;}

    private DistrictBoundary boundary(String city,String district,Geometry geometry){Envelope bounds=geometry.getEnvelopeInternal();boundaryGeometryCache.put(city+"/"+district,geometry);return new DistrictBoundary(city,district,geometryType(geometry),coordinates(geometry),new double[]{bounds.getMinX(),bounds.getMinY(),bounds.getMaxX(),bounds.getMaxY()},areaKm2(geometry));}
    private double areaKm2(Geometry geometry){try{return Math.round(JTS.transform(geometry,CRS.findMathTransform(WGS84,KOREA_2000,true)).getArea()/10_000d)/100d;}catch(Exception e){Envelope b=geometry.getEnvelopeInternal();return Math.round((b.getWidth()*88)*(b.getHeight()*111)*100d)/100d;}}

    private List<ParcelCandidate> diverse(List<ScoredCandidate> candidates) {
        if(candidates.isEmpty())return List.of();
        List<ParcelCandidate> selected = new ArrayList<>();
        selected.add(candidates.getFirst().candidate());
        while(selected.size()<20){ParcelCandidate next=candidates.stream().map(ScoredCandidate::candidate).filter(x->!selected.contains(x)).max(Comparator.comparingDouble(x->selected.stream().mapToDouble(y->distanceKm(x,y)).min().orElse(0))).orElse(null);if(next==null||selected.stream().mapToDouble(x->distanceKm(x,next)).min().orElse(0)<.5)break;selected.add(next);}
        return selected;
    }
    private double distanceKm(ParcelCandidate a,ParcelCandidate b){return Math.hypot((a.longitude()-b.longitude())*88,(a.latitude()-b.latitude())*111);}

    static STRtree spatialIndex(List<Geometry> geometries) {
        STRtree index = new STRtree();
        geometries.stream().map(SpatialDataService::valid).filter(geometry -> !geometry.isEmpty())
                .forEach(geometry -> index.insert(geometry.getEnvelopeInternal(), geometry));
        index.build();
        return index;
    }

    private static Geometry valid(Geometry geometry) {
        return geometry.isValid() ? geometry : GeometryFixer.fix(geometry);
    }

    static boolean intersectsAny(STRtree index, Geometry geometry) {
        for (Object candidate : index.query(geometry.getEnvelopeInternal()))
            if (geometry.intersects((Geometry) candidate)) return true;
        return false;
    }

    static boolean coveredByAny(STRtree index, Geometry geometry) {
        for (Object candidate : index.query(geometry.getEnvelopeInternal()))
            if (((Geometry) candidate).covers(geometry)) return true;
        return false;
    }

    static int nearbyCount(STRtree index, Geometry point, double radiusDegrees) {
        Envelope area = new Envelope(point.getEnvelopeInternal());
        area.expandBy(radiusDegrees);
        int count = 0;
        for (Object candidate : index.query(area))
            if (point.distance((Geometry) candidate) <= radiusDegrees) count++;
        return count;
    }

    private List<Geometry> loadActivityPoints(String district, Geometry districtGeometry) throws IOException {
        GeometryFactory factory = new GeometryFactory();
        PreparedGeometry districtArea = PreparedGeometryFactory.prepare(districtGeometry);
        List<Geometry> result = new ArrayList<>();
        Path businesses = findRawFile("소상공인시장진흥공단_상가(상권)정보_부산_");
        try (Reader reader = Files.newBufferedReader(businesses, StandardCharsets.UTF_8)) {
            for (CSVRecord row : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
                if (!district.equals(row.get("시군구명"))) continue;
                addActivityPoint(result, factory, districtArea, row.get("경도"), row.get("위도"));
            }
        }
        Path stops = findRawFile("국토교통부_전국 버스정류장 위치정보_");
        try (Reader reader = Files.newBufferedReader(stops, Charset.forName("MS949"))) {
            for (CSVRecord row : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
                if (!row.get("도시명").contains("부산")) continue;
                addActivityPoint(result, factory, districtArea, row.get("경도"), row.get("위도"));
            }
        }
        return result;
    }

    private void addActivityPoint(List<Geometry> result, GeometryFactory factory, PreparedGeometry district,
            String longitude, String latitude) {
        try {
            Geometry point = factory.createPoint(new Coordinate(Double.parseDouble(longitude), Double.parseDouble(latitude)));
            if (district.covers(point)) result.add(point);
        } catch (NumberFormatException ignored) { }
    }

    private List<Geometry> loadParkPoints(String city,String district,Geometry boundary)throws IOException{
        Path file=findRawFile("전국도시공원정보표준데이터");
        return coordinatePoints(file,Charset.forName("MS949"),boundary,row->{
            String address=row.get("소재지도로명주소")+" "+row.get("소재지지번주소");
            return address.contains(city+" "+district);
        });
    }

    private List<Geometry> loadBusStopPoints(String city,String district,Geometry boundary)throws IOException{
        Path file=findRawFile("국토교통부_전국 버스정류장 위치정보_");
        return coordinatePoints(file,Charset.forName("MS949"),boundary,row->row.get("도시명").contains(city.replace("광역시","")));
    }

    private List<Geometry> coordinatePoints(Path file,Charset charset,Geometry boundary,java.util.function.Predicate<CSVRecord> filter)throws IOException{
        GeometryFactory factory=new GeometryFactory();PreparedGeometry area=PreparedGeometryFactory.prepare(boundary);List<Geometry> result=new ArrayList<>();
        try(Reader reader=Files.newBufferedReader(file,charset);CSVParser parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)){
            for(CSVRecord row:parser)if(filter.test(row))addActivityPoint(result,factory,area,row.get("경도"),row.get("위도"));
        }
        return result;
    }

    static double averageNearestKm(List<ParcelCandidate> candidates,List<Geometry> facilities){
        if(candidates.isEmpty()||facilities.isEmpty())return 0;
        double average=candidates.stream().mapToDouble(candidate->facilities.stream().mapToDouble(facility->{Coordinate p=facility.getCoordinate();return Math.hypot((candidate.longitude()-p.x)*88,(candidate.latitude()-p.y)*111);}).min().orElse(0)).average().orElse(0);
        return Math.round(average*100)/100d;
    }

    private Path findRawFile(String prefix) throws IOException {
        try (var files = Files.list(raw)) {
            return files.filter(path -> path.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow();
        }
    }

    private List<Geometry> loadZoning(Geometry district) throws Exception {
        Path zip = raw.resolve("zoning/AL_D124_00_20260709.zip");
        Path dir = cache.resolve("zoning");
        extractZip(zip, dir);
        DataStore store = dataStore(firstShapefile(dir));
        try {
            SimpleFeatureSource source = store.getFeatureSource(store.getTypeNames()[0]);
            CoordinateReferenceSystem crs = source.getSchema().getCoordinateReferenceSystem();
            MathTransform fromWgs = CRS.findMathTransform(WGS84, crs, true);
            MathTransform toWgs = CRS.findMathTransform(crs, WGS84, true);
            Envelope box = JTS.transform(district, fromWgs).getEnvelopeInternal();
            PreparedGeometry districtArea=PreparedGeometryFactory.prepare(district);
            List<Geometry> result = new ArrayList<>();
            try (var features = source.getFeatures(query(source, box)).features()) {
                while (features.hasNext()) {
                    Geometry geometry = (Geometry) features.next().getDefaultGeometry();
                    if (geometry != null && !geometry.isEmpty()){Geometry wgs=JTS.transform(geometry,toWgs);if(districtArea.intersects(wgs))result.add(wgs);}
                }
            }
            return result;
        } finally { store.dispose(); }
    }

    private List<Geometry> loadFacilities(String city, Geometry district, List<String> warnings) throws Exception {
        Path outer = raw.resolve("planning-facilities/시설정보(도시계획)_20260622_전국.zip");
        if (!Files.exists(outer)) { warnings.add("도시계획시설 파일이 없습니다."); return List.of(); }
        Path dir = cache.resolve(CITY_CODES.get(city)).resolve("facilities");
        extractNestedZip(outer, CITY_CODES.get(city), dir);
        List<Geometry> result = new ArrayList<>();
        try (var paths = Files.walk(dir)) {
            for (Path shp : paths.filter(p -> p.toString().toLowerCase().endsWith(".shp")).toList()) {
                DataStore store = dataStore(shp);
                try {
                    SimpleFeatureSource source = store.getFeatureSource(store.getTypeNames()[0]);
                    CoordinateReferenceSystem crs = source.getSchema().getCoordinateReferenceSystem();
                    MathTransform fromWgs = CRS.findMathTransform(WGS84, crs, true);
                    MathTransform toWgs = CRS.findMathTransform(crs, WGS84, true);
                    Envelope box = JTS.transform(district, fromWgs).getEnvelopeInternal();
                    PreparedGeometry districtArea=PreparedGeometryFactory.prepare(district);
                    try (var features = source.getFeatures(query(source, box)).features()) {
                        while (features.hasNext()) {
                            Geometry geometry = (Geometry) features.next().getDefaultGeometry();
                            if (geometry != null && !geometry.isEmpty()){Geometry wgs=JTS.transform(geometry,toWgs);if(districtArea.intersects(wgs))result.add(wgs);}
                        }
                    }
                } catch (Exception ignored) { /* 손상된 단일 레이어는 건너뛴다. */ }
                finally { store.dispose(); }
            }
        }
        return result;
    }

    private Query query(SimpleFeatureSource source, Envelope box) {
        FilterFactory ff = CommonFactoryFinder.getFilterFactory();
        String geometry = source.getSchema().getGeometryDescriptor().getLocalName();
        return new Query(source.getSchema().getTypeName(), ff.bbox(geometry, box.getMinX(), box.getMinY(), box.getMaxX(), box.getMaxY(), null));
    }

    private DataStore dataStore(Path shp) throws IOException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("url", shp.toUri().toURL());
        parameters.put("charset", Charset.forName("EUC-KR"));
        DataStore store = DataStoreFinder.getDataStore(parameters);
        if (store == null) throw new IOException("SHP를 열 수 없습니다: " + shp);
        return store;
    }

    private Path firstShapefile(Path dir) throws IOException {
        try (var paths = Files.walk(dir)) {
            return paths.filter(p -> p.toString().toLowerCase().endsWith(".shp")).findFirst().orElseThrow();
        }
    }

    private void extractZip(Path zip, Path dir) throws IOException {
        if (Files.exists(dir.resolve(".done"))) return;
        Files.createDirectories(dir);
        try (InputStream input = Files.newInputStream(zip)) { unzip(new ZipInputStream(input), dir); }
        Files.createFile(dir.resolve(".done"));
    }

    private void extractNestedZip(Path outer, String cityCode, Path dir) throws IOException {
        if (Files.exists(dir.resolve(".done")) && hasShapefile(dir)) return;
        Files.createDirectories(dir);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(outer))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().contains(cityCode) && entry.getName().endsWith(".zip")) {
                    try (ZipInputStream nested = new ZipInputStream(new ByteArrayInputStream(zip.readAllBytes()))) { unzip(nested, dir); }
                    break;
                }
            }
        }
        if (!hasShapefile(dir)) throw new IOException(cityCode + " 도시계획시설 SHP가 없습니다.");
        if (!Files.exists(dir.resolve(".done"))) Files.createFile(dir.resolve(".done"));
    }

    private boolean hasShapefile(Path dir) throws IOException {
        try (var paths = Files.walk(dir)) { return paths.anyMatch(p -> p.toString().toLowerCase().endsWith(".shp")); }
    }

    private void unzip(ZipInputStream zip, Path dir) throws IOException {
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;
            Path target = dir.resolve(Path.of(entry.getName()).getFileName().toString());
            Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record ParcelCandidate(String parcelId, double longitude, double latitude, long areaM2) {}
    record ScoredCandidate(ParcelCandidate candidate, int activityCount, long population) {}
    private record CandidateKey(String city, String district) {}
    public record SpatialCandidates(String cityName, String districtName, int eligibleParcelCount,
            int nearbyPlanningFacilityCount, int nearbyZoningPolygonCount, double averageParkDistanceKm,
            double averageTransitDistanceKm, List<ParcelCandidate> candidates, List<String> warnings) {}
    public record DistrictBoundary(String cityName, String districtName, String type, Object coordinates, double[] bounds, double areaKm2) {}
}
