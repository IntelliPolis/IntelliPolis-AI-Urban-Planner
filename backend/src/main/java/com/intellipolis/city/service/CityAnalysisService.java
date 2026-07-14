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
 public CityAnalysisRequest sample(){return new CityAnalysisRequest("부산광역시","가상 해안구",320000L,48.5,22.4,14.2,8.1,12,34,8,2.3,1.8,1.1,List.of("해안대로","중앙로"),80000000000L,List.of("교통 혼잡 개선","녹지 접근성 향상"),new MapCoordinate(35.16,129.16),List.of(),List.of());}
 public CityAnalysisResponse create(CityAnalysisRequest req){
  UUID id=UUID.randomUUID(); CityScores scores=calculator.current(req); List<String>warnings=new ArrayList<>();
  var fallbackAgents=fallback(scores);var agents=new ArrayList<AgentAnalysis>();boolean anyFailed=false;
  try{String json=mapper.writeValueAsString(req);for(var old:fallbackAgents){var summary=ai.analyzeDomain(old.domain(),json);if(summary.isPresent())agents.add(new AgentAnalysis(old.domain(),old.score(),summary.get(),old.problems(),old.suggestions(),List.of()));else{agents.add(old);anyFailed=true;}}}
  catch(Exception e){agents.clear();agents.addAll(fallbackAgents);anyFailed=true;}
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
 private CityPlan defaultPlan(PlanType t,CityAnalysisRequest r){
  long cost=t==PlanType.COST_EFFECTIVE?r.totalBudget()/5:r.totalBudget()/3;
  var sites=sites(r,5);var types=switch(t){case BALANCED->List.of(FacilityType.TRANSIT_HUB,FacilityType.HOSPITAL,FacilityType.PARK,FacilityType.PUBLIC_SERVICE,FacilityType.CULTURE);case ECO_FOCUSED->List.of(FacilityType.PARK,FacilityType.PARK,FacilityType.TRANSIT_HUB,FacilityType.CULTURE,FacilityType.PUBLIC_SERVICE);case COST_EFFECTIVE->List.of(FacilityType.PUBLIC_SERVICE,FacilityType.TRANSIT_HUB,FacilityType.HOSPITAL,FacilityType.PARK);};
  long unitCost=types.isEmpty()?0:cost/types.size();List<PlannedFacility> facilities=new ArrayList<>();for(int i=0;i<types.size();i++){var type=types.get(i);var site=sites.get(i);facilities.add(new PlannedFacility(UUID.randomUUID().toString(),facilityName(type,i),type,PlanStatus.PROPOSED,site.longitude(),site.latitude(),type==FacilityType.PARK?8d:24d,unitCost,"실제 개발 가능 후보 필지 중 "+(i+1)+"순위 지점에 배치한 우선 검토 거점"));}
  double[] bounds=bounds(r,sites);double minX=bounds[0],minY=bounds[1],maxX=bounds[2],maxY=bounds[3];String congested=r.congestedRoads().isEmpty()?"주요 혼잡 구간":r.congestedRoads().get(0);var points=sites.stream().map(s->List.of(s.longitude(),s.latitude())).toList();
  var roads=List.of(new PlannedRoad(UUID.randomUUID().toString(),"서부 생활권 연결 검토선 · "+congested,t==PlanType.ECO_FOCUSED?RoadType.BUS:RoadType.BRT,PlanStatus.PROPOSED,List.of(points.get(0),points.get(2),points.get(3)),"서부 후보 거점과 중심 거점을 잇는 기존 도로 개선 검토선"),new PlannedRoad(UUID.randomUUID().toString(),"동부 생활권 연결 검토선",t==PlanType.ECO_FOCUSED?RoadType.BICYCLE:RoadType.BUS,PlanStatus.PROPOSED,List.of(points.get(1),points.get(2),points.get(4)),"동부 후보 거점과 중심 거점을 잇는 기존 도로 개선 검토선"),new PlannedRoad(UUID.randomUUID().toString(),"우선 거점 순환 검토선",t==PlanType.COST_EFFECTIVE?RoadType.BUS:RoadType.PEDESTRIAN,PlanStatus.PROPOSED,List.of(points.get(0),points.get(1),points.get(4),points.get(3),points.get(0)),"분산 배치한 우선 시설 사이의 접근성 보완 구간"));
  double d=Math.max(.001,Math.min(maxX-minX,maxY-minY)*.08);var first=sites.get(0);var zones=List.of(new PlannedZone(UUID.randomUUID().toString(),t==PlanType.ECO_FOCUSED?"녹지·보행 우선 구역":"복합 생활권 우선 개선 구역",t==PlanType.ECO_FOCUSED?ZoneType.GREEN:ZoneType.MIXED_USE,PlanStatus.PROPOSED,List.of(List.of(first.longitude()-d,first.latitude()-d),List.of(first.longitude()+d,first.latitude()-d),List.of(first.longitude()+d,first.latitude()+d),List.of(first.longitude()-d,first.latitude()+d),List.of(first.longitude()-d,first.latitude()-d)),"후보 필지와 도로축이 만나는 구역을 단계적으로 정비"));
  var score=calculator.planned(r,facilities,cost);
  return new CityPlan(t,switch(t){case BALANCED->"균형형";case ECO_FOCUSED->"환경 중심형";case COST_EFFECTIVE->"예산 효율형";},r.districtName()+" 후보 필지와 혼잡 정보를 반영한 우선 배치안","구 단위 도로 연결망을 먼저 제안하고 필요한 생활 거점을 단계적으로 배치",facilities,roads,zones,cost,List.of("구 단위 동서·남북 연결성 개선","후보 필지 기반 우선 시설 배치"),List.of("전체 시설 수요 예측이 아닌 우선 검토 거점","도로 신설 전 교통·환경 타당성 조사 필요"),List.of(new ImplementationPhase(1,"도로축 타당성 검토","혼잡 분산 효과와 기존 도로 활용 가능성을 검토합니다."),new ImplementationPhase(2,"우선 거점 사업화","후보 필지의 소유권과 접근성, 비용을 검토합니다.")),score);
 }
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
