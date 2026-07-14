package com.intellipolis.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class SpatialDataServiceTest {
    @Test
    void repeatedCandidateLookupUsesCache() {
        var service = new SpatialDataService("missing");

        var first = service.candidates("unsupported", "district", 127, 37);

        assertThat(service.candidates("unsupported", "district", 127, 37)).isSameAs(first);
    }

    @Test
    void spatialIndexKeepsExactGeometryChecks() {
        var geometries = new GeometryFactory();
        var area = geometries.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(2, 0), new Coordinate(2, 2),
                new Coordinate(0, 2), new Coordinate(0, 0)});
        var index = SpatialDataService.spatialIndex(java.util.List.of(area));

        assertThat(SpatialDataService.intersectsAny(index, geometries.createPoint(new Coordinate(1, 1)))).isTrue();
        assertThat(SpatialDataService.coveredByAny(index, geometries.createPoint(new Coordinate(3, 3)))).isFalse();
    }
}
