package com.intellipolis.ai.service;

import com.intellipolis.city.model.Enums.AnalysisDomain;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UrbanAiService {

 private static final String BASE_RULE = """
        당신은 IntelliPolis의 도시계획 의사결정 지원 에이전트입니다.
        제공된 데이터만 사용하고 입력에 없는 수치나 예산을 만들지 마세요.
        점수는 절대 스스로 계산하거나 언급하지 말고, 주어진 지표를 해석만 하세요.
        실제 행정 결정처럼 단정하지 말고 도시계획 전문가 검토가 필요한
        한국어 참고 제안으로 작성하세요. 3~4문장으로 간결하게 작성하세요.
        """;

 private static final Map<AnalysisDomain, String> DOMAIN_FOCUS = new EnumMap<>(AnalysisDomain.class);
 static {
  DOMAIN_FOCUS.put(AnalysisDomain.TRAFFIC, "당신은 교통 분석 에이전트입니다. 혼잡 도로, 대중교통 접근성, 이동 효율성 관점에서 해석하세요.");
  DOMAIN_FOCUS.put(AnalysisDomain.ENVIRONMENT, "당신은 환경 분석 에이전트입니다. 공원 면적 비율, 녹지 접근성, 보행 환경 관점에서 해석하세요.");
  DOMAIN_FOCUS.put(AnalysisDomain.ECONOMY, "당신은 경제 분석 에이전트입니다. 예산 규모, 상권, 인프라 투자 효율 관점에서 해석하세요.");
  DOMAIN_FOCUS.put(AnalysisDomain.LIVING, "당신은 생활 편의 분석 에이전트입니다. 병원·학교 접근성, 생활 인프라 관점에서 해석하세요.");
 }

 private final ChatClient client;

 public UrbanAiService(ObjectProvider<ChatModel> provider) {
  ChatModel model = provider.getIfAvailable();
  this.client = model == null ? null : ChatClient.create(model);
 }

 /** 도메인별 에이전트 분석 — 4개 도메인이 각각 다른 시스템 프롬프트로 호출됨 */
 public Optional<String> analyzeDomain(AnalysisDomain domain, String json) {
  if (client == null) return Optional.empty();
  String system = BASE_RULE + "\n" + DOMAIN_FOCUS.get(domain);
  try {
   String value = client.prompt().system(system)
           .user("다음 도시 데이터를 " + domain + " 관점에서 해석하세요: " + json)
           .call().content();
   return Optional.ofNullable(value).filter(s -> !s.isBlank());
  } catch (Exception ignored) {
   return Optional.empty();
  }
 }

 /** 최종 조정 에이전트 — 계획안 설명용 (기존 explain 역할 유지) */
 public Optional<String> explainPlan(String json) {
  if (client == null) return Optional.empty();
  String system = BASE_RULE + "\n당신은 최종 도시계획 조정 에이전트입니다. 계획안의 목적과 상충 관계를 설명하세요.";
  try {
   String value = client.prompt().system(system)
           .user("다음 규칙 기반 계획을 간결하게 설명하세요: " + json)
           .call().content();
   return Optional.ofNullable(value).filter(s -> !s.isBlank());
  } catch (Exception ignored) {
   return Optional.empty();
  }
 }
}