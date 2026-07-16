package com.intellipolis.city.service;

import com.intellipolis.city.dto.CityContracts.*;
import com.intellipolis.city.model.Enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties="spring.ai.model.chat=none")
class CityAnalysisServiceTest {
 @Autowired CityAnalysisService service;
 private CityPlan changed(CityPlan p,List<PlannedFacility> fs,List<PlannedZone> zs){return new CityPlan(p.planType(),p.name(),p.summary(),p.purpose(),fs,List.of(),zs,p.estimatedCost(),p.benefits(),p.tradeOffs(),p.phases(),p.expectedScores());}

 @Test void invalidCoordinateIsExcluded(){
  var a=service.create(service.sample());var p=a.plans().get(0);
  var invalid=new PlannedFacility("x","오류 시설",FacilityType.PARK,PlanStatus.PROPOSED,200d,95d,0d,0L,"좌표 오류");
  var updated=service.evaluate(a.analysisId(),p.planType(),changed(p,List.of(invalid),List.of()));
  assertThat(updated.plans().stream().filter(x->x.planType()==p.planType()).findFirst().orElseThrow().facilities()).isEmpty();
 }

 @Test void polygonIsClosedAutomatically(){
  var a=service.create(service.sample());var p=a.plans().get(0);
  var coords=List.of(List.of(129.1,35.1),List.of(129.2,35.1),List.of(129.2,35.2),List.of(129.1,35.2));
  var zone=new PlannedZone("z","개선 구역",ZoneType.GREEN,PlanStatus.PROPOSED,coords,"검토");
  var updated=service.evaluate(a.analysisId(),p.planType(),changed(p,List.of(),List.of(zone)));
  var ring=updated.plans().stream().filter(x->x.planType()==p.planType()).findFirst().orElseThrow().zones().get(0).coordinates();
  assertThat(ring.getFirst()).isEqualTo(ring.getLast());
 }

 @Test void planWithoutVerifiedSitesDoesNotInventScoreGain(){
  var a=service.create(service.sample());
  assertThat(a.plans()).allSatisfy(p->{assertThat(p.facilities()).isEmpty();assertThat(p.expectedScores().overall()).isGreaterThanOrEqualTo(a.currentScores().overall());});
 }

 @Test void evaluateRejectsMismatchedPlanType(){
  var a=service.create(service.sample());
  var eco=a.plans().stream().filter(p->p.planType()==PlanType.ECO_FOCUSED).findFirst().orElseThrow();
  org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->service.evaluate(a.analysisId(),PlanType.BALANCED,eco));
 }

 @Test void planUsesNeededFacilitiesAndMeasuredRoad(){
  var r=service.sample();
  var sites=List.of(new MapCoordinate(35.15,129.15),new MapCoordinate(35.16,129.16),new MapCoordinate(35.17,129.17));
  var line=List.of(List.of(129.15,35.15),List.of(129.17,35.17));
  var observations=List.of(new RoadObservation("1","중앙로",12d,1200d,"중앙교차로",45d,80L,line));
  var input=new CityAnalysisRequest(r.cityName(),r.districtName(),r.population(),r.areaKm2(),r.elderlyRatio(),r.youthRatio(),r.parkAreaRatio(),r.hospitalCount(),r.schoolCount(),r.transitHubCount(),r.averageHospitalDistanceKm(),r.averageParkDistanceKm(),r.averageTransitDistanceKm(),r.congestedRoads(),r.totalBudget(),r.priorityGoals(),r.mapCenter(),List.of(),sites,observations);
  var plan=service.create(input).plans().stream().filter(p->p.planType()==PlanType.BALANCED).findFirst().orElseThrow();
  assertThat(plan.facilities()).isNotEmpty();
  assertThat(plan.facilities()).allSatisfy(f->{assertThat(f.longitude()).isIn(sites.stream().map(MapCoordinate::longitude).toList());assertThat(f.reason()).doesNotContain("기존 도시계획시설");});
  assertThat(plan.roads()).hasSize(1);
  assertThat(plan.roads().getFirst().name()).isEqualTo("중앙로");
  assertThat(plan.roads().getFirst().coordinates()).isEqualTo(line);
  assertThat(plan.roads().getFirst().improvementType()).isEqualTo(RoadImprovementType.SIGNAL_OPTIMIZATION);
  assertThat(plan.roads().getFirst().evidence()).contains("공공 교통 API 관측 속도 12.0km/h");
  assertThat(plan.roads().getFirst().landImpact()).contains("정확한 정체 원인은 현장 조사 후 판단");
 }

 @Test void missingFacilityCountsDoNotCreateHospitalOrSchool(){
  var r=service.sample();
  var sites=List.of(new MapCoordinate(35.15,129.15),new MapCoordinate(35.16,129.16),new MapCoordinate(35.17,129.17),new MapCoordinate(35.18,129.18));
  var input=new CityAnalysisRequest(r.cityName(),r.districtName(),r.population(),r.areaKm2(),r.elderlyRatio(),r.youthRatio(),r.parkAreaRatio(),0,0,r.transitHubCount(),0d,r.averageParkDistanceKm(),r.averageTransitDistanceKm(),r.congestedRoads(),r.totalBudget(),r.priorityGoals(),r.mapCenter(),List.of(),sites,List.of());
  var facilities=service.create(input).plans().getFirst().facilities();
  assertThat(facilities).noneMatch(f->f.facilityType()==FacilityType.HOSPITAL||f.facilityType()==FacilityType.SCHOOL);
 }

 @Test void duplicateUnnamedAndUnreliableSlowRoadsAreNotProposed(){
  var r=service.sample();var line=List.of(List.of(129.15,35.15),List.of(129.17,35.17));
  var observations=List.of(new RoadObservation("1","중앙로",15d,0d,null,null,null,line),new RoadObservation("2","중앙로",8d,0d,null,null,null,line),new RoadObservation("3","-",5d,0d,null,null,null,line));
  var input=new CityAnalysisRequest(r.cityName(),r.districtName(),r.population(),r.areaKm2(),r.elderlyRatio(),r.youthRatio(),r.parkAreaRatio(),r.hospitalCount(),r.schoolCount(),r.transitHubCount(),r.averageHospitalDistanceKm(),r.averageParkDistanceKm(),r.averageTransitDistanceKm(),r.congestedRoads(),r.totalBudget(),r.priorityGoals(),r.mapCenter(),List.of(),List.of(),observations);
  var roads=service.create(input).plans().getFirst().roads();
  assertThat(roads).hasSize(1);
  assertThat(roads.getFirst().name()).isEqualTo("중앙로");
  assertThat(roads.getFirst().evidence()).contains("15.0km/h").doesNotContain("8.0km/h");
  assertThat(roads.getFirst().improvementType()).isEqualTo(RoadImprovementType.OPERATION_DIAGNOSIS);
 }

 @Test void facilityTypeUsesDifferentCandidateEvidence(){
  var r=service.sample();
  var large=new MapCoordinate(35.15,129.15,10000d,500,10000L);
  var active=new MapCoordinate(35.16,129.16,600d,1000,10000L);
  var sites=List.of(large,active);
  var parkInput=new CityAnalysisRequest(r.cityName(),r.districtName(),50_000L,r.areaKm2(),0d,0d,8d,99,99,1,0d,1.1d,.4d,List.of(),r.totalBudget(),r.priorityGoals(),r.mapCenter(),List.of(),sites,List.of());
  var transitInput=new CityAnalysisRequest(r.cityName(),r.districtName(),50_000L,r.areaKm2(),0d,0d,8d,99,99,0,0d,.4d,1.0d,List.of(),r.totalBudget(),r.priorityGoals(),r.mapCenter(),List.of(),sites,List.of());
  assertThat(service.create(parkInput).plans().getFirst().facilities()).filteredOn(f->f.facilityType()==FacilityType.GREEN_SHELTER||f.facilityType()==FacilityType.PARK).first().extracting(PlannedFacility::longitude).isEqualTo(large.longitude());
  assertThat(service.create(transitInput).plans().getFirst().facilities()).filteredOn(f->f.facilityType()==FacilityType.TRANSIT_HUB).first().extracting(PlannedFacility::longitude).isEqualTo(active.longitude());
 }
}
