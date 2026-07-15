package com.intellipolis.city.service;

import com.intellipolis.ai.service.UrbanAiService;
import com.intellipolis.city.dto.CityContracts.*;
import com.intellipolis.city.model.Enums.*;
import com.intellipolis.city.repository.CityAnalysisRepository;
import com.intellipolis.score.CityScoreCalculator;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
        return new CityAnalysisRequest("부산광역시", "가상 해안구", 320000L, 48.5, 22.4, 14.2, 8.1, 12, 34, 8, 2.3, 1.8, 1.1, List.of("해안대로", "중앙로"), 80000000000L, List.of("교통 혼잡 개선", "녹지 접근성 향상"), new MapCoordinate(35.16, 129.16), List.of(), List.of(), List.of());
    }

    public CityAnalysisResponse create(CityAnalysisRequest req) {
        UUID id = UUID.randomUUID();
        CityScores scores = calculator.current(req);
        List<String> warnings = new ArrayList<>();
        if (req.averageHospitalDistanceKm() == 0 || req.averageParkDistanceKm() == 0 || req.averageTransitDistanceKm() == 0)
            warnings.add("0으로 입력된 접근거리는 실제 0km가 아니라 미수집 값으로 처리했습니다.");
        var fallbackAgents = fallback(scores);
        List<AgentAnalysis> agents;
        boolean anyFailed;
        try {
            String json = mapper.writeValueAsString(req);
            var futures = fallbackAgents.stream().map(old -> CompletableFuture.supplyAsync(() -> {
                var summary = ai.analyzeDomain(old.domain(), json);
                return summary.map(s -> new AgentAnalysis(old.domain(), old.score(), s, old.problems(), old.suggestions(), List.of())).orElse(old);
            }).completeOnTimeout(old, 6, TimeUnit.SECONDS)).toList();
            agents = futures.stream().map(CompletableFuture::join).toList();
            anyFailed = agents.stream().anyMatch(x -> !x.warnings().isEmpty());
        } catch (Exception e) {
            agents = fallbackAgents;
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
        if (incoming.planType() != type) throw new IllegalArgumentException("요청한 계획 유형과 평가할 계획 유형이 일치하지 않습니다.");
        var old = repository.find(id);
        var req = requests.get(id);
        var clean = sanitize(incoming, req);
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
        return evidencePlan(t, r);
    }

    private CityPlan evidencePlan(PlanType type, CityAnalysisRequest r) {
        long budget = type == PlanType.COST_EFFECTIVE ? r.totalBudget() / 5 : r.totalBudget() / 3;
        List<FacilityType> needs = facilityNeeds(r, type);
        List<MapCoordinate> candidates = r.candidateSites() == null ? List.of() : r.candidateSites().stream().filter(MapCoordinate::valid).toList();
        int count = Math.min(needs.size(), candidates.size());
        long unit = count == 0 ? 0 : budget / count;
        List<PlannedFacility> facilities = new ArrayList<>();
        List<MapCoordinate> available = new ArrayList<>(candidates);
        List<MapCoordinate> selected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FacilityType f = needs.get(i);
            MapCoordinate s = selectDistributed(available, selected, r.mapCenter());
            available.remove(s);
            selected.add(s);
            int ordinal=(int)facilities.stream().filter(x->x.facilityType()==f).count()+1;
            facilities.add(new PlannedFacility(UUID.randomUUID().toString(), facilityName(f, ordinal), f, PlanStatus.PROPOSED, s.longitude(), s.latitude(), f == FacilityType.PARK ? 12d : 32d, unit, facilityReason(f, r) + " 이 위치는 상권이나 버스정류장이 가까워 실제 이용 가능성이 있고, 500㎡ 이상이면서 기존 도시계획시설과 겹치지 않는 필지입니다. 다만 최종 위치는 현장 조사 후 확정해야 합니다."));
        }
        List<PlannedRoad> roads = (r.roadObservations() == null ? List.<RoadObservation>of() : r.roadObservations()).stream().filter(o -> o.coordinates() != null && o.coordinates().size() >= 2).limit(type == PlanType.COST_EFFECTIVE ? 2 : 4).map(o -> roadPlan(o, type)).toList();
        List<String> limits = new ArrayList<>();
        if (needs.size() > candidates.size()) limits.add("필요 시설이 확인됐지만 검증된 후보지가 부족하여 지도에 임의 배치하지 않았습니다.");
        if (!facilities.isEmpty()) limits.add("시설 위치는 구 단위 부족 지표와 건설 가능 필지를 결합한 우선 검토 후보지이며, 생활권별 인구·기존 시설 접근성 조사 후 확정해야 합니다.");
        if (roads.isEmpty()) limits.add("실제 링크 좌표와 관측값이 없어 도로 개선선을 임의 생성하지 않았습니다.");
        limits.add("도로 확장·신설은 토지보상, 건축물 영향, 도시계획시설 결정 검토 전에는 실행안으로 확정할 수 없습니다.");
        String name = switch (type) {
            case BALANCED -> "균형형";
            case ECO_FOCUSED -> "환경 중심형";
            case COST_EFFECTIVE -> "예산 효율형";
        };
        String demand=needs.isEmpty()?"현재 시설 배치는 분석 기준상 적정하여 추가 시설을 제안하지 않습니다.":"필요 시설: "+needs.stream().collect(java.util.stream.Collectors.groupingBy(this::facilityTypeName,LinkedHashMap::new,java.util.stream.Collectors.counting())).entrySet().stream().map(x->x.getKey()+" "+x.getValue()+"개").collect(java.util.stream.Collectors.joining(", "));
        return new CityPlan(type, name, r.districtName() + "의 부족 지표와 실측 교통 링크만 반영한 검토안", demand, facilities, roads, List.of(), budget, List.of("필요성이 확인된 수량만 제안", "실제 혼잡 링크에 개선 수단 연결"), limits, List.of(new ImplementationPhase(1, "운영 개선 검증", "신호주기·차로운영·정류장 위치를 현장 조사와 교통 시뮬레이션으로 검증합니다."), new ImplementationPhase(2, "사업화 검토", "효과가 확인된 안에 한해 관계기관 협의와 예산·토지 영향을 검토합니다.")), calculator.planned(r, facilities, budget));
    }

    private List<FacilityType> facilityNeeds(CityAnalysisRequest r, PlanType type) {
        List<FacilityType> out = new ArrayList<>();
        add(out,FacilityType.TRANSIT_HUB,r.averageTransitDistanceKm()>1?quantity((r.averageTransitDistanceKm()-1)/.5,4):r.transitHubCount()==0?quantity(r.population()/100000d,4):0);
        int parkByArea=r.parkAreaRatio()<10?quantity((10-r.parkAreaRatio())/2.5,4):0,parkByDistance=r.averageParkDistanceKm()>1.2?quantity((r.averageParkDistanceKm()-1.2)/.5,4):0;add(out,FacilityType.PARK,Math.max(parkByArea,parkByDistance));
        add(out,FacilityType.HOSPITAL,r.averageHospitalDistanceKm()>2?quantity((r.averageHospitalDistanceKm()-2)/.75,3):r.hospitalCount()==0?quantity(r.population()/150000d,3):0);
        add(out,FacilityType.SCHOOL,r.schoolCount()==0&&r.youthRatio()>10?quantity(r.population()*r.youthRatio()/100/15000d,3):0);
        // 구 평균 고령비율만으로 특정 필지에 공공건물을 제안할 수 없으므로 생활권별 인구 자료가 생길 때까지 배치하지 않는다.
        if (type == PlanType.ECO_FOCUSED)
            out.sort(Comparator.comparingInt(x -> x == FacilityType.PARK ? 0 : x == FacilityType.TRANSIT_HUB ? 1 : 2));
        return out;
    }

    private void add(List<FacilityType> target,FacilityType type,int count){for(int i=0;i<count;i++)target.add(type);}
    private int quantity(double value,int max){return Math.min(max,Math.max(1,(int)Math.ceil(value)));}
    private MapCoordinate selectDistributed(List<MapCoordinate> candidates,List<MapCoordinate> selected,MapCoordinate center){
        if(selected.isEmpty()) return candidates.stream().max(Comparator.comparingDouble(x->distance(x,center))).orElseThrow();
        return candidates.stream().max(Comparator.comparingDouble(x->selected.stream().mapToDouble(y->distance(x,y)).min().orElse(0))).orElseThrow();
    }
    private double distance(MapCoordinate a,MapCoordinate b){double x=(a.longitude()-b.longitude())*88,y=(a.latitude()-b.latitude())*111;return Math.hypot(x,y);}
    private String facilityTypeName(FacilityType type){return switch(type){case TRANSIT_HUB->"환승거점";case PARK->"쉼터·공원";case HOSPITAL->"의료거점";case SCHOOL->"교육시설";case PUBLIC_SERVICE->"공공서비스";case CULTURE->"문화시설";};}

    private String facilityReason(FacilityType f, CityAnalysisRequest r) {
        return switch (f) {
            case TRANSIT_HUB -> r.averageTransitDistanceKm()>0?"주민의 평균 대중교통 접근거리가 " + r.averageTransitDistanceKm() + "km로 길어 환승 편의를 보완할 필요가 있습니다.":"현재 집계된 환승거점이 " + r.transitHubCount() + "개여서 교통 연결을 보완할 필요가 있습니다.";
            case PARK -> {
                double ratio = Math.round(r.parkAreaRatio() * 10.0) / 10.0;
                double shortage = Math.max(0, Math.round((10.0 - ratio) * 10.0) / 10.0);
                String areaExplanation = "현재 공원이 차지하는 면적은 분석 지역 전체 면적의 약 " + ratio + "%입니다. "
                        + "분석 기준으로 사용한 10%보다 약 " + shortage + "%p 부족합니다.";
                yield r.averageParkDistanceKm()>0
                        ? areaExplanation + " 주민이 공원까지 평균 " + r.averageParkDistanceKm() + "km를 이동해야 하므로, 생활권 가까이에 걸어서 이용할 수 있는 녹지 쉼터를 보완합니다."
                        : areaExplanation + " 따라서 주민이 일상에서 쉽게 걸어갈 수 있는 소규모 녹지 쉼터를 추가로 제안합니다.";
            }
            case HOSPITAL -> r.averageHospitalDistanceKm()>0?"병원은 " + r.hospitalCount() + "개이며 평균 의료 접근거리가 " + r.averageHospitalDistanceKm() + "km여서 가까운 의료 거점이 필요합니다.":"현재 집계된 병원이 " + r.hospitalCount() + "개여서 의료 접근 취약 가능성을 보완합니다.";
            case SCHOOL -> "학교가 0개로 집계됐습니다. 데이터 누락 여부를 먼저 확인하고 실제 부족이 확인될 때만 검토합니다.";
            case PUBLIC_SERVICE -> "고령인구 비율 " + r.elderlyRatio() + "%에 따른 생활지원 수요를 근거로 검토";
            case CULTURE -> "문화시설 수요조사 후 검토";
        };
    }

    private PlannedRoad roadPlan(RoadObservation o, PlanType plan) {
        RoadImprovementType improvement;
        FeasibilityLevel feasibility;
        String impact = "기존 도로 공간 안에서 검토하여 건축물 철거를 전제로 하지 않음";
        List<String> studies = new ArrayList<>(List.of("시간대별 방향별 교통량", "신호 현시 및 교차로 포화도", "버스·보행 안전 영향"));
        if (o.queueLength() != null && o.queueLength() >= 30) {
            improvement = RoadImprovementType.SIGNAL_OPTIMIZATION;
            feasibility = FeasibilityLevel.HIGH;
        } else if (o.pedestrianCount() != null && o.pedestrianCount() >= 100) {
            improvement = RoadImprovementType.PEDESTRIAN_SAFETY;
            feasibility = FeasibilityLevel.HIGH;
        } else if (o.volume() != null && o.volume() >= 1000) {
            improvement = plan == PlanType.ECO_FOCUSED ? RoadImprovementType.PUBLIC_TRANSIT : RoadImprovementType.LANE_OPERATION;
            feasibility = FeasibilityLevel.MEDIUM;
            studies.add("차로별 용량과 주정차 실태");
        } else {
            improvement = RoadImprovementType.DEMAND_MANAGEMENT;
            feasibility = FeasibilityLevel.MEDIUM;
            studies.add("주차·진입 수요 조사");
        }
        RoadType roadType = improvement == RoadImprovementType.PUBLIC_TRANSIT ? RoadType.BUS : improvement == RoadImprovementType.PEDESTRIAN_SAFETY ? RoadType.PEDESTRIAN : RoadType.ROAD;
        String evidence = "실측 속도 " + o.speedKmh() + "km/h, 교통량 " + o.volume() + "대" + (o.queueLength() == null ? "" : ", 대기행렬 " + o.queueLength() + "m") + (o.pedestrianCount() == null ? "" : ", 보행 " + o.pedestrianCount() + "명");
        return new PlannedRoad(UUID.randomUUID().toString(), o.roadName() + " 개선 검토", roadType, PlanStatus.PROPOSED, o.coordinates(), improvementReason(improvement), improvement, feasibility, evidence, impact, studies);
    }

    private String improvementReason(RoadImprovementType type) {
        return switch (type) {
            case SIGNAL_OPTIMIZATION -> "교차로 대기행렬을 우선 줄이도록 신호주기·좌회전 운영을 조정";
            case LANE_OPERATION -> "확장보다 차로 운영과 회전차로 구성을 먼저 검토";
            case PUBLIC_TRANSIT -> "버스 통행과 정류장·환승 운영을 개선해 승용차 수요를 분산";
            case PEDESTRIAN_SAFETY -> "횡단보도와 보행 동선을 우선 개선";
            case DEMAND_MANAGEMENT -> "주차·진입 관리로 첨두 교통량을 줄이는 방안을 우선 검토";
            case EXPANSION_REVIEW -> "운영 개선 후에도 용량 부족이 확인될 때만 확장 검토";
            case NEW_ROAD_REVIEW -> "대체 노선이 없고 편익이 확인될 때만 장기 신설 검토";
        };
    }

    private String facilityName(FacilityType type, int index) {
        return switch (type) {
            case TRANSIT_HUB -> "대중교통 환승 거점";
            case HOSPITAL -> "생활권 의료·돌봄 거점";
            case PARK -> "보행 녹지 쉼터 " + index;
            case PUBLIC_SERVICE -> "복합 공공서비스 거점";
            case CULTURE -> "지역 문화·청년 활동 거점";
            case SCHOOL -> "교육 복합 거점";
        };
    }

    private CityPlan sanitize(CityPlan p, CityAnalysisRequest r) {
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
