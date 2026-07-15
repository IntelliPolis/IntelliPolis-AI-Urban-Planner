package com.intellipolis.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class SpatialDataServiceTest {
    @Test
    void repeatedCandidateLookupUsesCache() {
        var service = new SpatialDataService("missing");

        var first = service.candidates("unsupported", "district");

        assertThat(service.candidates("unsupported", "district")).isSameAs(first);
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

    @Test
    void activityDensityExcludesIsolatedParcels() {
        var geometries = new GeometryFactory();
        var index = SpatialDataService.spatialIndex(java.util.List.of(
                geometries.createPoint(new Coordinate(129.00, 35.15)),
                geometries.createPoint(new Coordinate(129.001, 35.151)),
                geometries.createPoint(new Coordinate(129.002, 35.152))));

        assertThat(SpatialDataService.nearbyCount(index,
                geometries.createPoint(new Coordinate(129.001, 35.151)), 0.009)).isEqualTo(3);
        assertThat(SpatialDataService.nearbyCount(index,
                geometries.createPoint(new Coordinate(129.05, 35.20)), 0.009)).isZero();
    }

    @Test
    void spatialIndexRepairsInvalidSourcePolygon() {
        var geometries = new GeometryFactory();
        var bowTie = geometries.createPolygon(new Coordinate[]{new Coordinate(0, 0), new Coordinate(2, 2),
                new Coordinate(0, 2), new Coordinate(2, 0), new Coordinate(0, 0)});

        var index = SpatialDataService.spatialIndex(java.util.List.of(bowTie));

        assertThat(SpatialDataService.intersectsAny(index, geometries.createPoint(new Coordinate(1, .5)))).isTrue();
    }

    @Test
    void residentPopulationRaisesCandidateDemandScore() {
        var candidate = new SpatialDataService.ParcelCandidate("1", 129, 35, 500);

        assertThat(SpatialDataService.demandScore(new SpatialDataService.ScoredCandidate(candidate, 3, 20_000)))
                .isGreaterThan(SpatialDataService.demandScore(new SpatialDataService.ScoredCandidate(candidate, 3, 100)));
    }

    @Test
    void accessDistanceUsesNearestKnownFacility() {
        var candidate = new SpatialDataService.ParcelCandidate("1", 129, 35, 500);
        var facilities = java.util.List.<org.locationtech.jts.geom.Geometry>of(new GeometryFactory()
                .createPoint(new Coordinate(129.01, 35)));

        assertThat(SpatialDataService.averageNearestKm(java.util.List.of(candidate), facilities)).isBetween(.87, .89);
    }
}
