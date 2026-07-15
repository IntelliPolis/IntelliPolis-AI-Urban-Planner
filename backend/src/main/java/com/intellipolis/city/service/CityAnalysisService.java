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
            MapCoordinate s = selectCandidate(f,available, selected);
            available.remove(s);
            selected.add(s);
            int ordinal=(int)facilities.stream().filter(x->x.facilityType()==f).count()+1;
            facilities.add(new PlannedFacility(UUID.randomUUID().toString(), facilityName(f, ordinal), f, PlanStatus.PROPOSED, s.longitude(), s.latitude(), f == FacilityType.PARK ? 12d : 32d, unit, facilityReason(f, r) + candidateReason(f,s)));
        }
        List<PlannedRoad> roads = roadCandidates(r, type == PlanType.COST_EFFECTIVE ? 2 : 4).stream().map(o -> roadPlan(o, type)).toList();
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
        add(out,FacilityType.TRANSIT_HUB,r.averageTransitDistanceKm()>.7?quantity((r.averageTransitDistanceKm()-.7)/.4,3):0);
        int parkByArea=r.parkAreaRatio()<5?quantity((5-r.parkAreaRatio())/2.5,2):0;
        int parkByDistance=r.averageParkDistanceKm()>.8?quantity((r.averageParkDistanceKm()-.8)/.4,4):0;
        add(out,FacilityType.PARK,Math.max(parkByArea,parkByDistance));
        add(out,FacilityType.HOSPITAL,r.averageHospitalDistanceKm()>2?quantity((r.averageHospitalDistanceKm()-2)/.75,3):0);
        // 학교 수 0은 원자료 누락과 실제 부재를 구분할 수 없으므로 접근성 자료가 생길 때까지 위치를 제안하지 않는다.
        // 구 평균 고령비율만으로 특정 필지에 공공건물을 제안할 수 없으므로 생활권별 인구 자료가 생길 때까지 배치하지 않는다.
        if (type == PlanType.ECO_FOCUSED)
            out.sort(Comparator.comparingInt(x -> x == FacilityType.PARK ? 0 : x == FacilityType.TRANSIT_HUB ? 1 : 2));
        return out;
    }

    private void add(List<FacilityType> target,FacilityType type,int count){for(int i=0;i<count;i++)target.add(type);}
    private int quantity(double value,int max){return Math.min(max,Math.max(1,(int)Math.ceil(value-1e-9)));}
    private MapCoordinate selectCandidate(FacilityType type,List<MapCoordinate> candidates,List<MapCoordinate> selected){return candidates.stream().max(Comparator.comparingDouble(x->suitability(type,x)+(selected.isEmpty()?0:Math.min(4,selected.stream().mapToDouble(y->distance(x,y)).min().orElse(0))))).orElseThrow();}
    private double suitability(FacilityType type,MapCoordinate x){double area=Math.log1p(orZero(x.areaM2())),activity=Math.log1p(orZero(x.activityCount())),population=Math.log1p(orZero(x.population()));return switch(type){case PARK->area*2+population*2+activity*3;case TRANSIT_HUB->activity*4+population*3+area*.5;case HOSPITAL,PUBLIC_SERVICE->population*4+activity*2+area*.5;default->population+activity+area;};}
    private double orZero(Number value){return value==null?0:value.doubleValue();}
    private String candidateReason(FacilityType type,MapCoordinate x){if(x.areaM2()==null)return " 검증된 건설 가능 후보지를 구 전체에 분산한 위치이며 최종 입지는 현장 조사 후 확정해야 합니다.";String focus=switch(type){case PARK->"필지 면적과 생활권 인구";case TRANSIT_HUB->"생활 활동점과 인구";case HOSPITAL,PUBLIC_SERVICE->"생활권 인구와 활동 수요";default->"생활 수요";};return " "+focus+"를 우선 평가한 후보로, 필지 "+Math.round(x.areaM2())+"㎡·반경 약 1km 활동점 "+Math.round(orZero(x.activityCount()))+"개·해당 법정동 인구 "+Math.round(orZero(x.population()))+"명을 반영했습니다. 기존 도시계획시설과 겹치지 않지만 최종 입지는 현장 조사 후 확정해야 합니다.";}
    private double distance(MapCoordinate a,MapCoordinate b){double x=(a.longitude()-b.longitude())*88,y=(a.latitude()-b.latitude())*111;return Math.hypot(x,y);}
    private String facilityTypeName(FacilityType type){return switch(type){case TRANSIT_HUB->"환승거점";case PARK->"쉼터·공원";case HOSPITAL->"의료거점";case SCHOOL->"교육시설";case PUBLIC_SERVICE->"공공서비스";case CULTURE->"문화시설";};}

    private String facilityReason(FacilityType f, CityAnalysisRequest r) {
        return switch (f) {
            case TRANSIT_HUB -> "인구·활동성 기반 생활권 후보에서 가장 가까운 버스정류장까지 평균 직선거리가 " + oneDecimal(r.averageTransitDistanceKm()) + "km로, 환승 또는 정류장 접근 개선 검토가 필요합니다.";
            case PARK -> parkReason(r);
            case HOSPITAL -> r.averageHospitalDistanceKm()>0?"병원은 " + r.hospitalCount() + "개이며 생활권 후보에서 가장 가까운 병원까지 평균 직선거리가 " + r.averageHospitalDistanceKm() + "km여서 가까운 의료 거점이 필요합니다.":"현재 집계된 병원이 " + r.hospitalCount() + "개여서 의료 접근 취약 가능성을 보완합니다.";
            case SCHOOL -> "학교가 0개로 집계됐습니다. 데이터 누락 여부를 먼저 확인하고 실제 부족이 확인될 때만 검토합니다.";
            case PUBLIC_SERVICE -> "고령인구 비율 " + r.elderlyRatio() + "%에 따른 생활지원 수요를 근거로 검토";
            case CULTURE -> "문화시설 수요조사 후 검토";
        };
    }

    private String parkReason(CityAnalysisRequest r) {
        boolean far = r.averageParkDistanceKm() > .8;
        boolean scarce = r.parkAreaRatio() < 5;
        if (far && scarce) return "생활권 후보에서 가장 가까운 공원까지 평균 직선거리가 " + oneDecimal(r.averageParkDistanceKm()) + "km이고, 공원 면적 비율도 " + oneDecimal(r.parkAreaRatio()) + "%로 낮아 가까운 쉼터가 필요합니다.";
        if (far) return "공원 면적은 부족 기준에 해당하지 않지만, 생활권 후보에서 공원까지 평균 직선거리가 " + oneDecimal(r.averageParkDistanceKm()) + "km여서 쉼터 접근을 보완합니다.";
        return "평균 접근거리는 부족 기준에 해당하지 않지만, 공원 면적 비율이 " + oneDecimal(r.parkAreaRatio()) + "%로 낮아 생활권 녹지를 보완합니다.";
    }

    private String oneDecimal(double value) { return String.format(java.util.Locale.KOREA, "%.1f", value); }

    private List<RoadObservation> roadCandidates(CityAnalysisRequest request,int limit) {
        if(request.roadObservations()==null)return List.of();
        Map<String,RoadObservation> unique=new LinkedHashMap<>();
        request.roadObservations().stream().filter(o->o.roadName()!=null&&!o.roadName().isBlank()&&!"-".equals(o.roadName().trim())&&o.coordinates()!=null&&o.coordinates().size()>=2)
                .sorted(Comparator.comparing(RoadObservation::speedKmh,Comparator.nullsLast(Double::compareTo)))
                .forEach(o->unique.putIfAbsent(o.roadName().trim(),o));
        return unique.values().stream().limit(limit).toList();
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
            improvement = RoadImprovementType.OPERATION_DIAGNOSIS;
            feasibility = FeasibilityLevel.LOW;
            studies.add("반복 관측으로 상습 혼잡 여부 확인");
        }
        RoadType roadType = improvement == RoadImprovementType.PUBLIC_TRANSIT ? RoadType.BUS : improvement == RoadImprovementType.PEDESTRIAN_SAFETY ? RoadType.PEDESTRIAN : RoadType.ROAD;
        String confidence = o.intersectionName()!=null && ((o.queueLength()!=null&&o.queueLength()>0)||(o.pedestrianCount()!=null&&o.pedestrianCount()>0)||(o.volume()!=null&&o.volume()>0)) ? "높음" : "보통";
        String evidence = "근거 신뢰도 " + confidence + " · 실측 속도 " + o.speedKmh() + "km/h" + (o.volume() == null || o.volume() <= 0 ? "" : ", 교통량 " + o.volume() + "대") + (o.queueLength() == null || o.queueLength() <= 0 ? "" : ", 대기행렬 " + o.queueLength() + "m") + (o.pedestrianCount() == null || o.pedestrianCount() <= 0 ? "" : ", 보행 " + o.pedestrianCount() + "명");
        return new PlannedRoad(UUID.randomUUID().toString(), o.roadName() + " 개선 검토", roadType, PlanStatus.PROPOSED, o.coordinates(), improvementReason(improvement), improvement, feasibility, evidence, impact, studies);
    }

    private String improvementReason(RoadImprovementType type) {
        return switch (type) {
            case OPERATION_DIAGNOSIS -> "현재 저속은 확인됐지만 원인 자료가 부족해, 시간대별 반복 관측과 현장 조사 후 신호·차로·수요관리 중 적합한 수단을 결정";
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
