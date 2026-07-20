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
        for (int i = count; i < needs.size(); i++) {
            FacilityType f = needs.get(i);
            int ordinal=(int)facilities.stream().filter(x->x.facilityType()==f).count()+1;
            facilities.add(new PlannedFacility(UUID.randomUUID().toString(), facilityName(f, ordinal), f, PlanStatus.PROPOSED, null, null, 0d, 0L, facilityReason(f, r) + " 구 단위 검토 시설로 표시하며, 좌표가 검증되기 전까지 지도에는 임의 배치하지 않습니다."));
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
        return new CityPlan(type, name, r.districtName() + "의 부족 지표와 공공 교통 API 관측값만 반영한 검토안", demand, facilities, roads, List.of(), budget, List.of("필요성이 확인된 수량만 제안", "공공 교통 API 관측값을 기반으로 혼잡 후보 선별"), limits, List.of(new ImplementationPhase(1, "운영 개선 검증", "신호주기·차로운영·정류장 위치를 시간대별 관측과 현장 조사로 검증합니다."), new ImplementationPhase(2, "사업화 검토", "효과가 확인된 안에 한해 관계기관 협의와 예산·토지 영향을 검토합니다.")), calculator.planned(r, facilities, budget));
    }

    private List<FacilityType> facilityNeeds(CityAnalysisRequest r, PlanType type) {
        List<FacilityType> out = new ArrayList<>();
        add(out,FacilityType.TRANSIT_HUB,r.averageTransitDistanceKm()>.7?quantity((r.averageTransitDistanceKm()-.7)/.4,2):0);
        int parkByArea=r.parkAreaRatio()<5?quantity((5-r.parkAreaRatio())/2.5,2):0;
        int parkByDistance=r.averageParkDistanceKm()>.8?quantity((r.averageParkDistanceKm()-.8)/.4,2):0;
        // 보육시설은 기존 어린이집 POI/정원/대기수요 데이터가 있어야 타당하게 판단할 수 있다.
        // 현재 입력의 youthRatio는 학령·영유아 인구 비중일 뿐 기존 어린이집 공급을 차감하지 못하므로 자동 추천하지 않는다.
        // 공급 데이터 없이 인구 비율만으로 도서관·체육관·돌봄시설을 추천하면 이미 주변에 있는 시설도 중복 제안될 수 있다.
        // 현재 자동 추천은 기존 공급을 차감할 수 있는 접근거리/개수 지표가 있는 시설만 대상으로 한다.
        if(Math.max(parkByArea,parkByDistance)>0) addIfMissing(out,FacilityType.GREEN_SHELTER);
        if(r.averageHospitalDistanceKm()>1.5||r.hospitalCount()<Math.max(1,r.population()/50000)) addIfMissing(out,FacilityType.HEALTH_CENTER);
        if(r.elderlyRatio()>=18&&metricGoal(r,"노인요양시설")<Math.max(1,r.population()/30000)) addIfMissing(out,FacilityType.SENIOR_CARE);
        if(metricGoal(r,"공공체육시설")<Math.max(1,r.population()/60000)) addIfMissing(out,FacilityType.SPORTS_CENTER);
        if(r.population()>=80000&&r.transitHubCount()<Math.max(1,r.population()/80000)) addIfMissing(out,FacilityType.PARKING);
        if(r.transitHubCount()==0&&r.averageTransitDistanceKm()>.7) addIfMissing(out,FacilityType.TRANSIT_HUB);
        if(out.isEmpty()&&Math.max(parkByArea,parkByDistance)>0) addIfMissing(out,FacilityType.GREEN_SHELTER);
        out.sort(Comparator.comparingInt(this::facilityPriority));
        if(out.size()>8) out=new ArrayList<>(out.subList(0,8));
        if (type == PlanType.ECO_FOCUSED)
            out.sort(Comparator.comparingInt(x -> x == FacilityType.GREEN_SHELTER || x == FacilityType.PARK ? 0 : x == FacilityType.TRANSIT_HUB ? 1 : facilityPriority(x)));
        return out;
    }

    private void add(List<FacilityType> target,FacilityType type,int count){for(int i=0;i<count;i++)target.add(type);}
    private void addIfMissing(List<FacilityType> target,FacilityType type){if(!target.contains(type))target.add(type);}
    private long metricGoal(CityAnalysisRequest r,String name){return r.priorityGoals().stream().filter(x->x.startsWith(name+":")).map(x->x.substring(x.indexOf(':')+1)).mapToLong(x->{try{return Long.parseLong(x);}catch(NumberFormatException e){return 0;}}).findFirst().orElse(0);}
    private int facilityPriority(FacilityType x){return switch(x){case CHILDCARE->0;case SENIOR_CARE->1;case SPORTS_CENTER->2;case LIBRARY->3;case GREEN_SHELTER,PARK->4;case HEALTH_CENTER,HOSPITAL->5;case TRANSIT_HUB->6;case PARKING->7;case SCHOOL->8;case PUBLIC_SERVICE->9;case CULTURE->10;};}
    private int quantity(double value,int max){return Math.min(max,Math.max(1,(int)Math.ceil(value-1e-9)));}
    private MapCoordinate selectCandidate(FacilityType type,List<MapCoordinate> candidates,List<MapCoordinate> selected){List<MapCoordinate> pool=preferredCandidates(type,candidates);return pool.stream().max(Comparator.comparingDouble(x->suitability(type,x)+(selected.isEmpty()?0:Math.min(.8,selected.stream().mapToDouble(y->distance(x,y)).min().orElse(0)*.25)))).orElseThrow();}
    private List<MapCoordinate> preferredCandidates(FacilityType type,List<MapCoordinate> candidates){if(!needsLivingCore(type)||candidates.size()<4)return candidates;double minActivity=percentile(candidates.stream().mapToDouble(x->orZero(x.activityCount())).sorted().toArray(),.75);double minPopulation=percentile(candidates.stream().mapToDouble(x->orZero(x.population())).sorted().toArray(),.60);List<MapCoordinate> filtered=candidates.stream().filter(x->orZero(x.activityCount())>=minActivity&&orZero(x.population())>=minPopulation).toList();return filtered.isEmpty()?candidates:filtered;}
    private boolean needsLivingCore(FacilityType type){return switch(type){case CHILDCARE,SENIOR_CARE,SPORTS_CENTER,LIBRARY,SCHOOL,CULTURE,PUBLIC_SERVICE,HEALTH_CENTER->true;default->false;};}
    private double percentile(double[] values,double p){if(values.length==0)return 0;int i=(int)Math.floor((values.length-1)*p);return values[Math.max(0,Math.min(values.length-1,i))];}
    private double suitability(FacilityType type,MapCoordinate x){double area=Math.log1p(orZero(x.areaM2())),activity=Math.log1p(orZero(x.activityCount())),population=Math.log1p(orZero(x.population()));double livingCore=orZero(x.activityCount())*0.15+Math.log1p(orZero(x.population()))*3;return switch(type){case PARK,GREEN_SHELTER->area*2+population*2+activity*3;case TRANSIT_HUB,PARKING->activity*4+population*3+area*.5;case HOSPITAL,HEALTH_CENTER,SENIOR_CARE,PUBLIC_SERVICE->population*4+activity*3+area*.4+livingCore;case SPORTS_CENTER,LIBRARY,CHILDCARE,SCHOOL,CULTURE->population*3+activity*4+area*.2+livingCore;};}
    private double orZero(Number value){return value==null?0:value.doubleValue();}
    private String candidateReason(FacilityType type,MapCoordinate x){if(x.areaM2()==null)return " 구 전체 후보 중 생활권 접근성을 우선한 검토 위치입니다.";String focus=switch(type){case PARK,GREEN_SHELTER->"필지 면적과 생활권 인구";case TRANSIT_HUB,PARKING->"생활 활동점과 교통 수요";case HOSPITAL,HEALTH_CENTER,SENIOR_CARE,PUBLIC_SERVICE->"생활권 인구와 돌봄 수요";case CHILDCARE,SCHOOL,LIBRARY,SPORTS_CENTER,CULTURE->"생활권 인구와 학습·여가 수요";};return " "+focus+"를 우선 평가한 후보로, 필지 "+Math.round(x.areaM2())+"㎡·반경 약 1km 활동점 "+Math.round(orZero(x.activityCount()))+"개·해당 법정동 인구 "+Math.round(orZero(x.population()))+"명을 반영했습니다.";}
    private double distance(MapCoordinate a,MapCoordinate b){double x=(a.longitude()-b.longitude())*88,y=(a.latitude()-b.latitude())*111;return Math.hypot(x,y);}
    private String facilityTypeName(FacilityType type){return switch(type){case TRANSIT_HUB->"환승거점";case PARK,GREEN_SHELTER->"녹지·쉼터";case HOSPITAL,HEALTH_CENTER->"건강·의료";case SCHOOL,CHILDCARE->"보육·교육";case PUBLIC_SERVICE,SENIOR_CARE,SPORTS_CENTER->"생활SOC";case CULTURE,LIBRARY->"문화·학습";case PARKING->"공영주차";};}

    private String facilityReason(FacilityType f, CityAnalysisRequest r) {
        return switch (f) {
            case TRANSIT_HUB -> "인구·활동성 기반 생활권 후보에서 가장 가까운 버스정류장까지 평균 직선거리가 " + oneDecimal(r.averageTransitDistanceKm()) + "km로, 환승 또는 정류장 접근 개선 검토가 필요합니다.";
            case PARK -> parkReason(r);
            case HOSPITAL -> r.averageHospitalDistanceKm()>0?"병원은 " + r.hospitalCount() + "개이며 생활권 후보에서 가장 가까운 병원까지 평균 직선거리가 " + r.averageHospitalDistanceKm() + "km여서 가까운 의료 거점이 필요합니다.":"현재 집계된 병원이 " + r.hospitalCount() + "개여서 의료 접근 취약 가능성을 보완합니다.";
            case SCHOOL -> "영유아·학령 인구 비율 " + oneDecimal(r.youthRatio()) + "%를 반영해 보육·학습 돌봄 인프라 보완이 필요한 생활권 후보입니다.";
            case PUBLIC_SERVICE -> "주거 인구와 생활 활동점이 밀집한 후보지에 생활체육·돌봄·행정 서비스를 함께 제공하는 생활 SOC 거점을 검토합니다.";
            case CULTURE -> "학령·청년 생활수요와 기존 문화시설 접근성 보완을 위해 공공 도서관 또는 학습문화 거점을 검토합니다.";
            case CHILDCARE -> "영유아·학령 인구 비율 " + oneDecimal(r.youthRatio()) + "%를 반영해 보육시설 접근성과 대기 수요 보완이 필요한 생활권 후보입니다.";
            case SENIOR_CARE -> "고령인구 비율 " + oneDecimal(r.elderlyRatio()) + "%를 반영해 돌봄·여가·복지 서비스를 가까운 생활권 안에서 제공할 필요가 있습니다.";
            case SPORTS_CENTER -> "주거 밀집도와 생활 활동점 대비 생활체육 인프라가 부족할 수 있어 반경 1km 생활권 체육 거점을 검토합니다.";
            case LIBRARY -> "학령·청년 인구와 문화·학습 수요를 고려해 공공 도서관 또는 학습문화 거점을 검토합니다.";
            case GREEN_SHELTER -> parkReason(r);
            case HEALTH_CENTER -> "병원 접" +
                    "근성과 고령층 생활권을 고려해 예방·상담·기초 건강관리 기능을 가진 건강생활지원센터를 검토합니다.";
            case PARKING -> "상업시설과 혼잡 도로가 함께 많은 지역으로, 불법 주정차와 진입 교통을 줄이기 위한 공영주차장 후보를 검토합니다.";
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
                .filter(o->o.speedKmh()!=null&&o.speedKmh()>=10&&o.speedKmh()<30)
                .sorted(Comparator.comparing(RoadObservation::speedKmh,Comparator.nullsLast(Double::compareTo)))
                .forEach(o->unique.putIfAbsent(o.roadName().trim(),o));
        return unique.values().stream().limit(limit).toList();
    }

    private PlannedRoad roadPlan(RoadObservation o, PlanType plan) {
        RoadImprovementType improvement;
        FeasibilityLevel feasibility;
        String impact = "공공 교통 API 관측값 기반 혼잡 후보이며, 정확한 정체 원인은 현장 조사 후 판단";
        List<String> studies = new ArrayList<>(List.of("시간대별 API 관측 속도 반복 확인", "교차로 운영 현황", "우회 동선 가능성"));
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
        String speed = displaySpeed(o.speedKmh());
        String evidence = "공공 교통 API 관측 속도 " + speed;
        String reason = "공공 교통 API 관측값 기준 " + speed + "로, 혼잡 후보로 선별된 구간입니다. 시간대별 반복 확인 후 교차로 운영과 우회 동선 검토가 필요합니다.";
        return new PlannedRoad(UUID.randomUUID().toString(), o.roadName(), roadType, PlanStatus.PROPOSED, o.coordinates(), reason, improvement, feasibility, evidence, impact, studies);
    }

    private String displaySpeed(Double speed) {
        if (speed == null) return "자료 없음";
        return oneDecimal(speed) + "km/h";
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
            case PARK -> "근린공원";
            case PUBLIC_SERVICE -> "생활 SOC 체육관";
            case CULTURE -> "공공 도서관";
            case SCHOOL -> "국공립 어린이집";
            case CHILDCARE -> "국공립 어린이집";
            case SENIOR_CARE -> "노인복지관·돌봄센터";
            case SPORTS_CENTER -> "생활 SOC 체육관";
            case LIBRARY -> "공공 도서관";
            case GREEN_SHELTER -> "근린공원·녹지 쉼터";
            case HEALTH_CENTER -> "건강생활지원센터";
            case PARKING -> "공영주차장";
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
