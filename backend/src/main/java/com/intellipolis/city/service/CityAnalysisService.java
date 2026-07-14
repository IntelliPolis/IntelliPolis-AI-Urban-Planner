package com.intellipolis.city.service;

import tools.jackson.databind.ObjectMapper;
import com.intellipolis.ai.service.UrbanAiService;
import com.intellipolis.city.dto.CityContracts.*;
import com.intellipolis.city.model.Enums.*;
import com.intellipolis.city.repository.CityAnalysisRepository;
import com.intellipolis.score.CityScoreCalculator;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CityAnalysisService {
    private final CityAnalysisRepository repository;
    private final CityScoreCalculator calculator;
    private final UrbanAiService ai;
    private final ObjectMapper mapper;
    private final Map<UUID, CityAnalysisRequest> requests = new ConcurrentHashMap<>();

    public CityAnalysisService(CityAnalysisRepository r, CityScoreCalculator c, UrbanAiService ai, ObjectMapper m) {
        repository = r;
        calculator = c;
        this.ai = ai;
        mapper = m;
    }

    public CityAnalysisRequest sample() {
        return new CityAnalysisRequest("부산광역시", "가상 해안구", 320000L, 48.5, 22.4, 14.2, 8.1, 12, 34, 8, 2.3, 1.8, 1.1, List.of("해안대로", "중앙로"), 80000000000L, List.of("교통 혼잡 개선", "녹지 접근성 향상"), new MapCoordinate(35.16, 129.16), List.of());
    }

    public CityAnalysisResponse create(CityAnalysisRequest req) {
        UUID id = UUID.randomUUID();
        CityScores scores = calculator.current(req);
        List<String> warnings = new ArrayList<>();
        List<AgentAnalysis> fallbackAgents = fallback(scores);
        List<AgentAnalysis> agents = new ArrayList<>();
        boolean anyFailed = false;

        try {
            String json = mapper.writeValueAsString(req);
            for (AgentAnalysis old : fallbackAgents) {
                var aiSummary = ai.analyzeDomain(old.domain(), json);
                if (aiSummary.isPresent()) {
                    agents.add(new AgentAnalysis(old.domain(), old.score(), aiSummary.get(),
                            old.problems(), old.suggestions(), old.warnings()));
                } else {
                    agents.add(old);
                    anyFailed = true;
                }
            }
        } catch (Exception e) {
            agents.addAll(fallbackAgents);
            anyFailed = true;
        }
        if (anyFailed) warnings.add("일부 분야는 Gemini를 사용할 수 없어 규칙 기반 분석으로 대체했습니다.");

        var response = new CityAnalysisResponse(id, req.cityName(), req.districtName(), scores, agents, plans(req), warnings, OffsetDateTime.now());
        requests.put(id, req);
        return repository.save(response);
    }

    public CityAnalysisResponse get(UUID id) {
        return repository.find(id);
    }

    public CityAnalysisResponse evaluate(UUID id, PlanType type, CityPlan incoming) {
        var old = repository.find(id);
        var req = requests.get(id);
        var clean = sanitize(incoming, req, new ArrayList<>());
        List<CityPlan> plans = new ArrayList<>(old.plans());
        plans.removeIf(p -> p.planType() == type);
        plans.add(clean);
        return repository.save(new CityAnalysisResponse(old.analysisId(), old.cityName(), old.districtName(), old.currentScores(), old.agentAnalyses(), plans, old.warnings(), old.createdAt()));
    }

    public Map<String, String> explain(UUID id, PlanType type) {
        CityPlan p = repository.find(id).plans().stream().filter(x -> x.planType() == type).findFirst().orElseThrow(() -> new IllegalArgumentException("계획안 타입이 올바르지 않습니다."));
        try {
            return Map.of("explanation", ai.explainPlan(mapper.writeValueAsString(p)).orElse("이 계획안은 현재 지표를 바탕으로 " + p.purpose() + "을 목표로 하는 규칙 기반 대안입니다."));
        } catch (Exception e) {
            return Map.of("explanation", "계획 정보를 바탕으로 생성한 규칙 기반 설명입니다.");
        }
    }

    private List<AgentAnalysis> fallback(CityScores s) {
        return List.of(agent(AnalysisDomain.TRAFFIC, s.traffic()), agent(AnalysisDomain.ENVIRONMENT, s.environment()), agent(AnalysisDomain.ECONOMY, s.economy()), agent(AnalysisDomain.LIVING, s.living()));
    }

    private AgentAnalysis agent(AnalysisDomain d, int score) {
        return new AgentAnalysis(d, score, d + " 분야의 입력 지표를 규칙으로 분석했습니다.", List.of(new UrbanProblem("지표 검토 필요", "상대적으로 낮은 지표를 전문가가 검토해야 합니다.", score < 60 ? Severity.HIGH : Severity.MEDIUM, "규칙 기반 점수 " + score)), List.of(new UrbanSuggestion("단계적 개선", "기존 시설을 우선 활용해 개선안을 검토합니다.", "접근성과 비용 효율 검토", CostLevel.MEDIUM)), List.of("AI 분석 대신 fallback 결과입니다."));
    }

    private List<CityPlan> plans(CityAnalysisRequest r) {
        return Arrays.stream(PlanType.values()).map(t -> defaultPlan(t, r)).toList();
    }

    private CityPlan defaultPlan(PlanType t, CityAnalysisRequest r) {
        FacilityType ft = t == PlanType.ECO_FOCUSED ? FacilityType.PARK : t == PlanType.COST_EFFECTIVE ? FacilityType.PUBLIC_SERVICE : FacilityType.TRANSIT_HUB;
        long cost = t == PlanType.COST_EFFECTIVE ? r.totalBudget() / 5 : r.totalBudget() / 3;
        double x = r.mapCenter().longitude(), y = r.mapCenter().latitude(), d = .002;
        var facilities = List.of(new PlannedFacility(UUID.randomUUID().toString(), ft == FacilityType.PARK ? "생활권 연결 공원" : "생활권 개선 시설", ft, PlanStatus.PROPOSED, x, y, 38d, cost / 2, "분석 우선 목표를 반영한 신규 거점"), new PlannedFacility(UUID.randomUUID().toString(), "복합 생활 서비스 거점", FacilityType.PUBLIC_SERVICE, PlanStatus.PROPOSED, x + d, y + d / 2, 24d, cost / 2, "기존 상권과 보행권을 연결"));
        var roads = List.of(new PlannedRoad(UUID.randomUUID().toString(), "생활권 연결축", t == PlanType.ECO_FOCUSED ? RoadType.BICYCLE : RoadType.BRT, PlanStatus.PROPOSED, List.of(List.of(x - d * 1.5, y - d), List.of(x, y), List.of(x + d * 1.5, y + d)), "신규 거점과 기존 중심지를 연결"));
        var zones = List.of(new PlannedZone(UUID.randomUUID().toString(), "우선 개선 구역", t == PlanType.ECO_FOCUSED ? ZoneType.GREEN : ZoneType.REDEVELOPMENT, PlanStatus.PROPOSED, List.of(List.of(x - d, y - d), List.of(x + d, y - d), List.of(x + d, y + d), List.of(x - d, y + d), List.of(x - d, y - d)), "단계적 정비가 필요한 후보 구역"));
        var score = calculator.planned(r, facilities, cost);
        return new CityPlan(t, switch (t) {
            case BALANCED -> "균형형";
            case ECO_FOCUSED -> "환경 중심형";
            case COST_EFFECTIVE -> "예산 효율형";
        }, "규칙 기반 기본 계획", "분야별 지표의 현실적인 개선", facilities, roads, zones, cost, List.of("생활권 접근성 개선"), List.of("전문가 검토 필요"), List.of(new ImplementationPhase(1, "후보지 검토", "부지와 비용을 검토합니다.")), score);
    }

    private CityPlan sanitize(CityPlan p, CityAnalysisRequest r, List<String> warnings) {
        List<PlannedFacility> fs = p.facilities() == null ? List.of() : p.facilities().stream().filter(f -> f.latitude() != null && f.longitude() != null && f.latitude() >= -90 && f.latitude() <= 90 && f.longitude() >= -180 && f.longitude() <= 180).toList();
        List<PlannedRoad> roads = p.roads() == null ? List.of() : p.roads().stream().filter(x -> x.coordinates() != null && x.coordinates().size() >= 2 && x.coordinates().stream().allMatch(this::validPair)).toList();
        List<PlannedZone> zones = p.zones() == null ? List.of() : p.zones().stream().map(this::closeZone).filter(Objects::nonNull).toList();
        long cost = p.estimatedCost() == null ? 0 : p.estimatedCost();
        return new CityPlan(p.planType(), p.name(), p.summary(), p.purpose(), fs, roads, zones, cost, p.benefits(), p.tradeOffs(), p.phases(), calculator.planned(r, fs, cost));
    }

    private boolean validPair(List<Double> x) {
        return x != null && x.size() >= 2 && x.get(0) >= -180 && x.get(0) <= 180 && x.get(1) >= -90 && x.get(1) <= 90;
    }

    private PlannedZone closeZone(PlannedZone z) {
        if (z.coordinates() == null || z.coordinates().size() < 3 || !z.coordinates().stream().allMatch(this::validPair))
            return null;
        var c = new ArrayList<>(z.coordinates());
        if (!c.get(0).equals(c.get(c.size() - 1))) c.add(c.get(0));
        return c.size() < 4 ? null : new PlannedZone(z.id(), z.name(), z.zoneType(), z.status(), c, z.reason());
    }
}
