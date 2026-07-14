package com.intellipolis.traffic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TrafficDataServiceTest {
    @Test
    void missingKeyFailsBeforeCallingExternalApi() {
        var service = new TrafficDataService("", "", new RoadGeometryService("data/raw"));

        assertThat(service.status().get("configured")).isEqualTo(false);
        assertThatThrownBy(() -> service.summary("부산광역시", "해운대구", 129.16, 35.16))
                .isInstanceOf(TrafficDataException.class)
                .hasMessage("부산 교통 API 키가 설정되지 않았습니다.");
    }
}
