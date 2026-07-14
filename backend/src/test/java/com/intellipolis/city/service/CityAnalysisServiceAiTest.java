package com.intellipolis.city.service;

import com.intellipolis.ai.service.UrbanAiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = "spring.ai.model.chat=none")
class CityAnalysisServiceAiTest {

    @Autowired
    CityAnalysisService service;
    @MockitoBean
    UrbanAiService ai;

    @Test
    void aiSuccessReplacesSummaryPerDomain() {
        given(ai.analyzeDomain(any(), any())).willReturn(Optional.of("AI 생성 요약"));
        var result = service.create(service.sample());
        assertThat(result.agentAnalyses()).allMatch(a -> a.summary().equals("AI 생성 요약"));
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void aiFailureFallsBackWithWarning() {
        given(ai.analyzeDomain(any(), any())).willReturn(Optional.empty());
        var result = service.create(service.sample());
        assertThat(result.warnings()).isNotEmpty();
    }
}