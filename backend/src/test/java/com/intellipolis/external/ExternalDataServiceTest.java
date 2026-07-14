package com.intellipolis.external;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExternalDataServiceTest {
    @Test
    void missingKeyFailsBeforeCallingExternalApi() {
        var service = new ExternalDataService("", "", "");

        assertThatThrownBy(() -> service.hospitals("110000", "110001"))
                .isInstanceOf(ExternalDataException.class)
                .hasMessage("공공데이터포털 API 키가 설정되지 않았습니다.");
        assertThatThrownBy(() -> service.schools("B10", "강남구"))
                .isInstanceOf(ExternalDataException.class)
                .hasMessage("나이스 API 키가 설정되지 않았습니다.");
    }
}
