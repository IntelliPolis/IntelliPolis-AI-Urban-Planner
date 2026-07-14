package com.intellipolis.spatial;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
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
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SpatialDataService {
    private static final double SEARCH_RADIUS_M = 3000;
    private static final CoordinateReferenceSystem WGS84;
    private static final Map<String, String> CADASTRAL = Map.of("부산광역시", "LSMD_CONT_LDREG_부산.zip");
    private static final Map<String, String> CITY_CODES = Map.of("부산광역시", "26000");
    static {
        try { WGS84 = CRS.decode("EPSG:4326", true); }
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

    public SpatialCandidates candidates(String city, String district, double longitude, double latitude) {
        return candidateCache.computeIfAbsent(new CandidateKey(city, district, longitude, latitude),
                ignored -> loadCandidates(city, district, longitude, latitude));
    }

    private SpatialCandidates loadCandidates(String city, String district, double longitude, double latitude) {
        List<String> warnings = new ArrayList<>();
        try {
            String filename = CADASTRAL.get(city);
            if (filename == null) return new SpatialCandidates(city, district, 0, 0, 0, List.of(), List.of("부산광역시만 지원합니다."));
            Path cadastral = cache.resolve(CITY_CODES.get(city)).resolve("cadastral");
            extractZip(raw.resolve("cadastral").resolve(filename), cadastral);
            List<Geometry> facilities = loadFacilities(city, longitude, latitude, warnings);
            List<Geometry> zoning = loadZoning(longitude, latitude);
            List<ParcelCandidate> parcels = loadParcels(cadastral, longitude, latitude, facilities, zoning);
            warnings.add("선택 지점 반경 약 3km에서 500㎡ 이상이며 도시계획시설과 겹치지 않는 필지를 골랐습니다.");
            warnings.add("국토계획/도시지역 전체데이터와 겹치는 필지만 후보로 사용했습니다.");
            return new SpatialCandidates(city, district, parcels.size(), facilities.size(), zoning.size(), parcels.stream().limit(20).toList(), warnings);
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
            String prefix = Files.readAllLines(raw.resolve("법정동코드 전체자료.txt"), Charset.forName("MS949")).stream()
                    .map(line -> line.split("\t"))
                    .filter(row -> row.length >= 3 && row[1].equals(city + " " + district))
                    .map(row -> row[0].substring(0, 5)).findFirst()
                    .orElseThrow(() -> new IOException("법정동코드를 찾지 못했습니다."));
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

    private List<ParcelCandidate> loadParcels(Path dir, double lon, double lat, List<Geometry> facilities, List<Geometry> zoning) throws Exception {
        List<ParcelCandidate> result = new ArrayList<>();
        STRtree facilityIndex = spatialIndex(facilities);
        STRtree zoningIndex = spatialIndex(zoning);
        DataStore store = dataStore(firstShapefile(dir));
        try {
            SimpleFeatureSource source = store.getFeatureSource(store.getTypeNames()[0]);
            CoordinateReferenceSystem crs = source.getSchema().getCoordinateReferenceSystem();
            MathTransform fromWgs = CRS.findMathTransform(WGS84, crs, true);
            MathTransform toWgs = CRS.findMathTransform(crs, WGS84, true);
            Geometry center = JTS.transform(new GeometryFactory().createPoint(new Coordinate(lon, lat)), fromWgs);
            Envelope box = new Envelope(center.getCoordinate()); box.expandBy(SEARCH_RADIUS_M);
            try (var features = source.getFeatures(query(source, box)).features()) {
                while (features.hasNext()) {
                    SimpleFeature feature = features.next();
                    Geometry geometry = (Geometry) feature.getDefaultGeometry();
                    if (geometry == null || geometry.isEmpty() || geometry.getArea() < 500) continue;
                    Geometry wgsGeometry = JTS.transform(geometry, toWgs);
                    if (intersectsAny(facilityIndex, wgsGeometry)) continue;
                    Geometry interiorPoint = wgsGeometry.getInteriorPoint();
                    if (!coveredByAny(zoningIndex, interiorPoint)) continue;
                    Coordinate point = interiorPoint.getCoordinate();
                    Object pnu = feature.getAttribute("PNU");
                    result.add(new ParcelCandidate(pnu == null ? feature.getID() : pnu.toString(), point.x, point.y,
                            Math.round(geometry.getArea()), Math.round(geometry.distance(center))));
                }
            }
        } finally { store.dispose(); }
        result.sort(Comparator.comparingLong(ParcelCandidate::distanceM).thenComparing(Comparator.comparingLong(ParcelCandidate::areaM2).reversed()));
        return diverse(result);
    }

    private DistrictBoundary boundary(String city,String district,Geometry geometry){Envelope bounds=geometry.getEnvelopeInternal();boundaryGeometryCache.put(city+"/"+district,geometry);return new DistrictBoundary(city,district,geometryType(geometry),coordinates(geometry),new double[]{bounds.getMinX(),bounds.getMinY(),bounds.getMaxX(),bounds.getMaxY()});}

    private List<ParcelCandidate> diverse(List<ParcelCandidate> candidates) {
        List<ParcelCandidate> selected = new ArrayList<>();
        for (ParcelCandidate candidate : candidates) {
            boolean nearby = selected.stream().anyMatch(old -> Math.hypot((old.longitude()-candidate.longitude())*88,(old.latitude()-candidate.latitude())*111)<.5);
            if (!nearby) selected.add(candidate);
            if (selected.size() == 20) break;
        }
        return selected;
    }

    static STRtree spatialIndex(List<Geometry> geometries) {
        STRtree index = new STRtree();
        geometries.forEach(geometry -> index.insert(geometry.getEnvelopeInternal(), geometry));
        index.build();
        return index;
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

    private List<Geometry> loadZoning(double lon, double lat) throws Exception {
        Path zip = raw.resolve("zoning/AL_D124_00_20260709.zip");
        Path dir = cache.resolve("zoning");
        extractZip(zip, dir);
        DataStore store = dataStore(firstShapefile(dir));
        try {
            SimpleFeatureSource source = store.getFeatureSource(store.getTypeNames()[0]);
            CoordinateReferenceSystem crs = source.getSchema().getCoordinateReferenceSystem();
            MathTransform fromWgs = CRS.findMathTransform(WGS84, crs, true);
            MathTransform toWgs = CRS.findMathTransform(crs, WGS84, true);
            Geometry center = JTS.transform(new GeometryFactory().createPoint(new Coordinate(lon, lat)), fromWgs);
            Envelope box = new Envelope(center.getCoordinate()); box.expandBy(SEARCH_RADIUS_M);
            List<Geometry> result = new ArrayList<>();
            try (var features = source.getFeatures(query(source, box)).features()) {
                while (features.hasNext()) {
                    Geometry geometry = (Geometry) features.next().getDefaultGeometry();
                    if (geometry != null && !geometry.isEmpty()) result.add(JTS.transform(geometry, toWgs));
                }
            }
            return result;
        } finally { store.dispose(); }
    }

    private List<Geometry> loadFacilities(String city, double lon, double lat, List<String> warnings) throws Exception {
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
                    Geometry center = JTS.transform(new GeometryFactory().createPoint(new Coordinate(lon, lat)), fromWgs);
                    Envelope box = new Envelope(center.getCoordinate()); box.expandBy(SEARCH_RADIUS_M);
                    try (var features = source.getFeatures(query(source, box)).features()) {
                        while (features.hasNext()) {
                            Geometry geometry = (Geometry) features.next().getDefaultGeometry();
                            if (geometry != null && !geometry.isEmpty()) result.add(JTS.transform(geometry, toWgs));
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

    public record ParcelCandidate(String parcelId, double longitude, double latitude, long areaM2, long distanceM) {}
    private record CandidateKey(String city, String district, double longitude, double latitude) {}
    public record SpatialCandidates(String cityName, String districtName, int eligibleParcelCount,
            int nearbyPlanningFacilityCount, int nearbyZoningPolygonCount, List<ParcelCandidate> candidates, List<String> warnings) {}
    public record DistrictBoundary(String cityName, String districtName, String type, Object coordinates, double[] bounds) {}
}
