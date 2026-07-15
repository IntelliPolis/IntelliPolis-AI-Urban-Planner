package com.intellipolis.traffic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TrafficDataServiceTest {
    @Test
    void missingKeyFailsBeforeCallingExternalApi() {
        var service = new TrafficDataService("", "", new RoadGeometryService("data/raw"), null);

        assertThat(service.status().get("configured")).isEqualTo(false);
        assertThatThrownBy(() -> service.summary("부산광역시", "해운대구", 129.16, 35.16))
                .isInstanceOf(TrafficDataException.class)
                .hasMessage("교통정보 API 키가 설정되지 않았습니다.");
    }

    @Test
    void roadsOutsideSelectedAreaAreExcluded() {
        assertThat(TrafficDataService.inArea(java.util.List.of(java.util.List.of(129.16, 35.16)), 129.15, 35.15)).isTrue();
        assertThat(TrafficDataService.inArea(java.util.List.of(java.util.List.of(128.90, 35.30)), 129.15, 35.15)).isFalse();
        assertThat(TrafficDataService.inArea(null, 129.15, 35.15)).isFalse();
    }

    @Test
    void intersectionMustBeWithinAboutTwoHundredMetersOfRoad() {
        var road = java.util.List.of(java.util.List.of(129.000, 35.150), java.util.List.of(129.010, 35.150));
        assertThat(TrafficDataService.distanceSquared(road, 129.001, 35.150)).isLessThanOrEqualTo(.000004);
        assertThat(TrafficDataService.distanceSquared(road, 129.020, 35.150)).isGreaterThan(.000004);
    }
}
