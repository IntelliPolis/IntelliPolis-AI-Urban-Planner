package com.intellipolis.traffic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TrafficDataServiceTest {
    @Test
    void missingKeyFailsBeforeCallingExternalApi() {
        var service = new TrafficDataService("");

        assertThat(service.status().get("configured")).isEqualTo(false);
        assertThatThrownBy(() -> service.summary("서울특별시", "강남구", 127, 37))
                .isInstanceOf(TrafficDataException.class)
                .hasMessage("ITS API 키가 설정되지 않았습니다.");
    }
}
