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
 private final CityAnalysisRepository repository; private final CityScoreCalculator calculator; private final UrbanAiService ai; private final ObjectMapper mapper;
 private final Map<UUID,CityAnalysisRequest> requests=new ConcurrentHashMap<>();
 public CityAnalysisService(CityAnalysisRepository r,CityScoreCalculator c,UrbanAiService ai,ObjectMapper m){repository=r;calculator=c;this.ai=ai;mapper=m;}
 public CityAnalysisRequest sample(){return new CityAnalysisRequest("부산광역시","가상 해안구",320000L,48.5,22.4,14.2,8.1,12,34,8,2.3,1.8,1.1,List.of("해안대로","중앙로"),80000000000L,List.of("교통 혼잡 개선","녹지 접근성 향상"),new MapCoordinate(35.16,129.16),List.of(),List.of(),List.of());}
 public CityAnalysisResponse create(CityAnalysisRequest req){
  UUID id=UUID.randomUUID(); CityScores scores=calculator.current(req); List<String>warnings=new ArrayList<>();
  var fallbackAgents=fallback(scores);List<AgentAnalysis> agents;boolean anyFailed;
  try{String json=mapper.writeValueAsString(req);agents=fallbackAgents.parallelStream().map(old->{var summary=ai.analyzeDomain(old.domain(),json);return summary.map(s->new AgentAnalysis(old.domain(),old.score(),s,old.problems(),old.suggestions(),List.<String>of())).orElse(old);}).toList();anyFailed=agents.stream().anyMatch(x->!x.warnings().isEmpty());}
  catch(Exception e){agents=fallbackAgents;anyFailed=true;}
  if(anyFailed)warnings.add("일부 분야는 Gemini를 사용할 수 없어 규칙 기반 분석으로 대체했습니다.");
  var response=new CityAnalysisResponse(id,req.cityName(),req.districtName(),scores,agents,plans(req),warnings,OffsetDateTime.now());
  requests.put(id,req);return repository.save(response);
 }
 public CityAnalysisResponse get(UUID id){return repository.find(id);}
 public CityAnalysisResponse evaluate(UUID id,PlanType type,CityPlan incoming){
  if(incoming.planType()!=type)throw new IllegalArgumentException("요청한 계획 유형과 평가할 계획 유형이 일치하지 않습니다.");
  var old=repository.find(id);var req=requests.get(id);var clean=sanitize(incoming,req,new ArrayList<>());
  List<CityPlan> plans=new ArrayList<>(old.plans());plans.removeIf(p->p.planType()==type);plans.add(clean);
  return repository.save(new CityAnalysisResponse(old.analysisId(),old.cityName(),old.districtName(),old.currentScores(),old.agentAnalyses(),plans,old.warnings(),old.createdAt()));
 }
 public Map<String,String> explain(UUID id,PlanType type){
  CityPlan p=repository.find(id).plans().stream().filter(x->x.planType()==type).findFirst().orElseThrow(()->new IllegalArgumentException("계획안 타입이 올바르지 않습니다."));
  try{return Map.of("explanation",ai.explainPlan(mapper.writeValueAsString(p)).orElse("이 계획안은 현재 지표를 바탕으로 "+p.purpose()+"을 목표로 하는 규칙 기반 대안입니다."));}
  catch(Exception e){return Map.of("explanation","계획 정보를 바탕으로 생성한 규칙 기반 설명입니다.");}
 }
 private List<AgentAnalysis> fallback(CityScores s){return List.of(agent(AnalysisDomain.TRAFFIC,s.traffic()),agent(AnalysisDomain.ENVIRONMENT,s.environment()),agent(AnalysisDomain.ECONOMY,s.economy()),agent(AnalysisDomain.LIVING,s.living()));}
 private AgentAnalysis agent(AnalysisDomain d,int score){return new AgentAnalysis(d,score,d+" 분야의 입력 지표를 규칙으로 분석했습니다.",List.of(new UrbanProblem("지표 검토 필요","상대적으로 낮은 지표를 전문가가 검토해야 합니다.",score<60?Severity.HIGH:Severity.MEDIUM,"규칙 기반 점수 "+score)),List.of(new UrbanSuggestion("단계적 개선","기존 시설을 우선 활용해 개선안을 검토합니다.","접근성과 비용 효율 검토",CostLevel.MEDIUM)),List.of("AI 분석 대신 fallback 결과입니다."));}
 private List<CityPlan> plans(CityAnalysisRequest r){return Arrays.stream(PlanType.values()).map(t->defaultPlan(t,r)).toList();}
 private CityPlan defaultPlan(PlanType t,CityAnalysisRequest r){return evidencePlan(t,r);}
 private CityPlan legacyPlan(PlanType t,CityAnalysisRequest r){
  long cost=t==PlanType.COST_EFFECTIVE?r.totalBudget()/5:r.totalBudget()/3;
  var sites=sites(r,5);var types=switch(t){case BALANCED->List.of(FacilityType.TRANSIT_HUB,FacilityType.HOSPITAL,FacilityType.PARK,FacilityType.PUBLIC_SERVICE,FacilityType.CULTURE);case ECO_FOCUSED->List.of(FacilityType.PARK,FacilityType.PARK,FacilityType.TRANSIT_HUB,FacilityType.CULTURE,FacilityType.PUBLIC_SERVICE);case COST_EFFECTIVE->List.of(FacilityType.PUBLIC_SERVICE,FacilityType.TRANSIT_HUB,FacilityType.HOSPITAL,FacilityType.PARK);};
  long unitCost=types.isEmpty()?0:cost/types.size();List<PlannedFacility> facilities=new ArrayList<>();for(int i=0;i<types.size();i++){var type=types.get(i);var site=sites.get(i);facilities.add(new PlannedFacility(UUID.randomUUID().toString(),facilityName(type,i),type,PlanStatus.PROPOSED,site.longitude(),site.latitude(),type==FacilityType.PARK?8d:24d,unitCost,"실제 개발 가능 후보 필지 중 "+(i+1)+"순위 지점에 배치한 우선 검토 거점"));}
  double[] bounds=bounds(r,sites);double minX=bounds[0],minY=bounds[1],maxX=bounds[2],maxY=bounds[3];String congested=r.congestedRoads().isEmpty()?"주요 혼잡 구간":r.congestedRoads().get(0);var points=sites.stream().map(s->List.of(s.longitude(),s.latitude())).toList();
  var roads=List.of(new PlannedRoad(UUID.randomUUID().toString(),"서부 생활권 연결 검토선 · "+congested,t==PlanType.ECO_FOCUSED?RoadType.BUS:RoadType.BRT,PlanStatus.PROPOSED,List.of(points.get(0),points.get(2),points.get(3)),"서부 후보 거점과 중심 거점을 잇는 기존 도로 개선 검토선"),new PlannedRoad(UUID.randomUUID().toString(),"동부 생활권 연결 검토선",t==PlanType.ECO_FOCUSED?RoadType.BICYCLE:RoadType.BUS,PlanStatus.PROPOSED,List.of(points.get(1),points.get(2),points.get(4)),"동부 후보 거점과 중심 거점을 잇는 기존 도로 개선 검토선"),new PlannedRoad(UUID.randomUUID().toString(),"우선 거점 순환 검토선",t==PlanType.COST_EFFECTIVE?RoadType.BUS:RoadType.PEDESTRIAN,PlanStatus.PROPOSED,List.of(points.get(0),points.get(1),points.get(4),points.get(3),points.get(0)),"분산 배치한 우선 시설 사이의 접근성 보완 구간"));
  double d=Math.max(.001,Math.min(maxX-minX,maxY-minY)*.08);var first=sites.get(0);var zones=List.of(new PlannedZone(UUID.randomUUID().toString(),t==PlanType.ECO_FOCUSED?"녹지·보행 우선 구역":"복합 생활권 우선 개선 구역",t==PlanType.ECO_FOCUSED?ZoneType.GREEN:ZoneType.MIXED_USE,PlanStatus.PROPOSED,List.of(List.of(first.longitude()-d,first.latitude()-d),List.of(first.longitude()+d,first.latitude()-d),List.of(first.longitude()+d,first.latitude()+d),List.of(first.longitude()-d,first.latitude()+d),List.of(first.longitude()-d,first.latitude()-d)),"후보 필지와 도로축이 만나는 구역을 단계적으로 정비"));
  var score=calculator.planned(r,facilities,cost);
  return new CityPlan(t,switch(t){case BALANCED->"균형형";case ECO_FOCUSED->"환경 중심형";case COST_EFFECTIVE->"예산 효율형";},r.districtName()+" 후보 필지와 혼잡 정보를 반영한 우선 배치안","구 단위 도로 연결망을 먼저 제안하고 필요한 생활 거점을 단계적으로 배치",facilities,roads,zones,cost,List.of("구 단위 동서·남북 연결성 개선","후보 필지 기반 우선 시설 배치"),List.of("전체 시설 수요 예측이 아닌 우선 검토 거점","도로 신설 전 교통·환경 타당성 조사 필요"),List.of(new ImplementationPhase(1,"도로축 타당성 검토","혼잡 분산 효과와 기존 도로 활용 가능성을 검토합니다."),new ImplementationPhase(2,"우선 거점 사업화","후보 필지의 소유권과 접근성, 비용을 검토합니다.")),score);
 }
 private CityPlan evidencePlan(PlanType type,CityAnalysisRequest r){
  long budget=type==PlanType.COST_EFFECTIVE?r.totalBudget()/5:r.totalBudget()/3;
  List<FacilityType> needs=facilityNeeds(r,type);List<MapCoordinate> candidates=r.candidateSites()==null?List.of():r.candidateSites().stream().filter(MapCoordinate::valid).toList();
  int count=Math.min(needs.size(),candidates.size());long unit=count==0?0:budget/count;List<PlannedFacility> facilities=new ArrayList<>();
  for(int i=0;i<count;i++){FacilityType f=needs.get(i);MapCoordinate s=candidates.get(i);facilities.add(new PlannedFacility(UUID.randomUUID().toString(),facilityName(f,i),f,PlanStatus.PROPOSED,s.longitude(),s.latitude(),f==FacilityType.PARK?8d:24d,unit,facilityReason(f,r)));}
  List<PlannedRoad> roads=(r.roadObservations()==null?List.<RoadObservation>of():r.roadObservations()).stream().filter(o->o.coordinates()!=null&&o.coordinates().size()>=2).limit(type==PlanType.COST_EFFECTIVE?2:4).map(o->roadPlan(o,type)).toList();
  List<String> limits=new ArrayList<>();if(needs.size()>candidates.size())limits.add("필요 시설이 확인됐지만 검증된 후보지가 부족하여 지도에 임의 배치하지 않았습니다.");if(roads.isEmpty())limits.add("실제 링크 좌표와 관측값이 없어 도로 개선선을 임의 생성하지 않았습니다.");limits.add("도로 확장·신설은 토지보상, 건축물 영향, 도시계획시설 결정 검토 전에는 실행안으로 확정할 수 없습니다.");
  String name=switch(type){case BALANCED->"균형형";case ECO_FOCUSED->"환경 중심형";case COST_EFFECTIVE->"예산 효율형";};
  return new CityPlan(type,name,r.districtName()+"의 부족 지표와 실측 교통 링크만 반영한 검토안","모든 시설을 고정 제시하지 않고 부족한 항목과 실행 가능한 도로 운영 개선부터 검토",facilities,roads,List.of(),budget,List.of("필요성이 확인된 항목만 제안","실제 혼잡 링크에 개선 수단 연결"),limits,List.of(new ImplementationPhase(1,"운영 개선 검증","신호주기·차로운영·정류장 위치를 현장 조사와 교통 시뮬레이션으로 검증합니다."),new ImplementationPhase(2,"사업화 검토","효과가 확인된 안에 한해 관계기관 협의와 예산·토지 영향을 검토합니다.")),calculator.planned(r,facilities,budget));
 }
 private List<FacilityType> facilityNeeds(CityAnalysisRequest r,PlanType type){
  List<FacilityType> out=new ArrayList<>();if(r.averageTransitDistanceKm()>1||r.transitHubCount()==0)out.add(FacilityType.TRANSIT_HUB);if(r.parkAreaRatio()<10||r.averageParkDistanceKm()>1.2)out.add(FacilityType.PARK);if(r.averageHospitalDistanceKm()>2||r.hospitalCount()==0)out.add(FacilityType.HOSPITAL);if(r.schoolCount()==0&&r.youthRatio()>10)out.add(FacilityType.SCHOOL);if(r.elderlyRatio()>20)out.add(FacilityType.PUBLIC_SERVICE);
  if(type==PlanType.ECO_FOCUSED)out.sort(Comparator.comparingInt(x->x==FacilityType.PARK?0:x==FacilityType.TRANSIT_HUB?1:2));if(type==PlanType.COST_EFFECTIVE&&out.size()>2)return out.subList(0,2);return out;
 }
 private String facilityReason(FacilityType f,CityAnalysisRequest r){return switch(f){case TRANSIT_HUB->"평균 대중교통 접근거리 "+r.averageTransitDistanceKm()+"km와 환승거점 "+r.transitHubCount()+"개를 근거로 검토";case PARK->"공원면적 비율 "+r.parkAreaRatio()+"%와 평균 공원 접근거리 "+r.averageParkDistanceKm()+"km를 근거로 검토";case HOSPITAL->"병원 "+r.hospitalCount()+"개와 평균 의료 접근거리 "+r.averageHospitalDistanceKm()+"km를 근거로 검토";case SCHOOL->"학교 수가 0으로 집계되어 원자료 누락 여부를 먼저 확인한 뒤 검토";case PUBLIC_SERVICE->"고령인구 비율 "+r.elderlyRatio()+"%에 따른 생활지원 수요를 근거로 검토";case CULTURE->"문화시설 수요조사 후 검토";};}
 private PlannedRoad roadPlan(RoadObservation o,PlanType plan){
  RoadImprovementType improvement;FeasibilityLevel feasibility;String impact="기존 도로 공간 안에서 검토하여 건축물 철거를 전제로 하지 않음";List<String> studies=new ArrayList<>(List.of("시간대별 방향별 교통량","신호 현시 및 교차로 포화도","버스·보행 안전 영향"));
  if(o.queueLength()!=null&&o.queueLength()>=30){improvement=RoadImprovementType.SIGNAL_OPTIMIZATION;feasibility=FeasibilityLevel.HIGH;}
  else if(o.pedestrianCount()!=null&&o.pedestrianCount()>=100){improvement=RoadImprovementType.PEDESTRIAN_SAFETY;feasibility=FeasibilityLevel.HIGH;}
  else if(o.volume()!=null&&o.volume()>=1000){improvement=plan==PlanType.ECO_FOCUSED?RoadImprovementType.PUBLIC_TRANSIT:RoadImprovementType.LANE_OPERATION;feasibility=FeasibilityLevel.MEDIUM;studies.add("차로별 용량과 주정차 실태");}
  else{improvement=RoadImprovementType.DEMAND_MANAGEMENT;feasibility=FeasibilityLevel.MEDIUM;studies.add("주차·진입 수요 조사");}
  RoadType roadType=improvement==RoadImprovementType.PUBLIC_TRANSIT?RoadType.BUS:improvement==RoadImprovementType.PEDESTRIAN_SAFETY?RoadType.PEDESTRIAN:RoadType.ROAD;
  String evidence="실측 속도 "+o.speedKmh()+"km/h, 교통량 "+o.volume()+"대"+(o.queueLength()==null?"":", 대기행렬 "+o.queueLength()+"m")+(o.pedestrianCount()==null?"":", 보행 "+o.pedestrianCount()+"명");
  return new PlannedRoad(UUID.randomUUID().toString(),o.roadName()+" 개선 검토",roadType,PlanStatus.PROPOSED,o.coordinates(),improvementReason(improvement),improvement,feasibility,evidence,impact,studies);
 }
 private String improvementReason(RoadImprovementType type){return switch(type){case SIGNAL_OPTIMIZATION->"교차로 대기행렬을 우선 줄이도록 신호주기·좌회전 운영을 조정";case LANE_OPERATION->"확장보다 차로 운영과 회전차로 구성을 먼저 검토";case PUBLIC_TRANSIT->"버스 통행과 정류장·환승 운영을 개선해 승용차 수요를 분산";case PEDESTRIAN_SAFETY->"횡단보도와 보행 동선을 우선 개선";case DEMAND_MANAGEMENT->"주차·진입 관리로 첨두 교통량을 줄이는 방안을 우선 검토";case EXPANSION_REVIEW->"운영 개선 후에도 용량 부족이 확인될 때만 확장 검토";case NEW_ROAD_REVIEW->"대체 노선이 없고 편익이 확인될 때만 장기 신설 검토";};}
 private List<MapCoordinate> sites(CityAnalysisRequest r,int count){var source=r.candidateSites()==null?List.<MapCoordinate>of():r.candidateSites().stream().filter(MapCoordinate::valid).toList();List<MapCoordinate> result=new ArrayList<>();double x=r.mapCenter().longitude(),y=r.mapCenter().latitude();for(int i=0;i<count;i++)result.add(i<source.size()?source.get(i):new MapCoordinate(y+Math.sin(i*2.4)*.008,x+Math.cos(i*2.4)*.008));return result;}
 private double[] bounds(CityAnalysisRequest r,List<MapCoordinate> sites){var points=r.boundary().isEmpty()?sites:r.boundary();double minX=points.stream().mapToDouble(MapCoordinate::longitude).min().orElse(r.mapCenter().longitude()-.02),maxX=points.stream().mapToDouble(MapCoordinate::longitude).max().orElse(r.mapCenter().longitude()+.02),minY=points.stream().mapToDouble(MapCoordinate::latitude).min().orElse(r.mapCenter().latitude()-.02),maxY=points.stream().mapToDouble(MapCoordinate::latitude).max().orElse(r.mapCenter().latitude()+.02);return new double[]{minX,minY,maxX,maxY};}
 private String facilityName(FacilityType type,int index){return switch(type){case TRANSIT_HUB->"대중교통 환승 거점";case HOSPITAL->"생활권 의료·돌봄 거점";case PARK->index==0?"생활권 연결 공원":"보행 녹지 쉼터";case PUBLIC_SERVICE->"복합 공공서비스 거점";case CULTURE->"지역 문화·청년 활동 거점";case SCHOOL->"교육 복합 거점";};}
 private CityPlan sanitize(CityPlan p,CityAnalysisRequest r,List<String>warnings){
  List<PlannedFacility> fs=p.facilities()==null?List.of():p.facilities().stream().filter(f->f.latitude()!=null&&f.longitude()!=null&&f.latitude()>=-90&&f.latitude()<=90&&f.longitude()>=-180&&f.longitude()<=180).toList();
  List<PlannedRoad> roads=p.roads()==null?List.of():p.roads().stream().filter(x->x.coordinates()!=null&&x.coordinates().size()>=2&&x.coordinates().stream().allMatch(this::validPair)).toList();
  List<PlannedZone> zones=p.zones()==null?List.of():p.zones().stream().map(this::closeZone).filter(Objects::nonNull).toList();
  long cost=p.estimatedCost()==null?0:p.estimatedCost();
  return new CityPlan(p.planType(),p.name(),p.summary(),p.purpose(),fs,roads,zones,cost,p.benefits(),p.tradeOffs(),p.phases(),calculator.planned(r,fs,cost));
 }
 private boolean validPair(List<Double>x){return x!=null&&x.size()>=2&&x.get(0)>=-180&&x.get(0)<=180&&x.get(1)>=-90&&x.get(1)<=90;}
 private PlannedZone closeZone(PlannedZone z){if(z.coordinates()==null||z.coordinates().size()<3||!z.coordinates().stream().allMatch(this::validPair))return null;var c=new ArrayList<>(z.coordinates());if(!c.get(0).equals(c.get(c.size()-1)))c.add(c.get(0));return c.size()<4?null:new PlannedZone(z.id(),z.name(),z.zoneType(),z.status(),c,z.reason());}
}
