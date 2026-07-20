package com.intellipolis.score;

import com.intellipolis.city.dto.CityContracts.CityAnalysisRequest;
import com.intellipolis.city.dto.CityContracts.CityScores;
import com.intellipolis.city.dto.CityContracts.PlannedFacility;
import com.intellipolis.city.model.Enums.FacilityType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 프로젝트 비교용 설명 가능한 휴리스틱이며
 * 실제 도시계획 평가 모델이 아닙니다.
 */
@Component
public class CityScoreCalculator {

 /**
  * 현재 도시 상태 점수 계산
  */
 public CityScores current(CityAnalysisRequest r) {

  int traffic = clamp(
          68
                  - distancePenalty(
                  r.averageTransitDistanceKm(),
                  0.5,
                  12
          )
                  + densityBonus(
                  r.transitHubCount(),
                  r.population(),
                  2,
                  15
          )
                  - Math.min(
                  30,
                  r.congestedRoads() == null
                          ? 0
                          : r.congestedRoads().size() * 3
          )
  );

  int environment = clamp(
          45
                  + (int) (r.parkAreaRatio() * 3)
                  - distancePenalty(
                  r.averageParkDistanceKm(),
                  0.5,
                  10
          )
  );

  int living = clamp(
          45
                  + densityBonus(
                  r.hospitalCount(),
                  r.population(),
                  2,
                  18
          )
                  + densityBonus(
                  r.schoolCount(),
                  r.population(),
                  1,
                  18
          )
                  - distancePenalty(
                  r.averageHospitalDistanceKm(),
                  1.0,
                  8
          )
  );

  /*
   * 경제활동, 고용률, 재정자립도 등의 자료가 없으므로
   * 현재 경제 점수는 비교 기준값인 50점으로 둡니다.
   */
  int economy = 50;

  return scores(
          traffic,
          environment,
          economy,
          living
  );
 }

 /**
  * 계획안 적용 후 예상 점수 계산
  */
 public CityScores planned(
         CityAnalysisRequest r,
         List<PlannedFacility> facilities,
         long cost
 ) {
  CityScores base = current(r);

  List<PlannedFacility> safeFacilities =
          facilities == null ? List.of() : facilities;

  long environmentFacilities = safeFacilities.stream()
          .filter(f ->
                  f.facilityType() == FacilityType.PARK
                          || f.facilityType() == FacilityType.GREEN_SHELTER
          )
          .count();

  long medicalFacilities = safeFacilities.stream()
          .filter(f ->
                  f.facilityType() == FacilityType.HOSPITAL
                          || f.facilityType() == FacilityType.HEALTH_CENTER
          )
          .count();

  long livingFacilities = safeFacilities.stream()
          .filter(f ->
                  f.facilityType() == FacilityType.PUBLIC_SERVICE
                          || f.facilityType() == FacilityType.SENIOR_CARE
                          || f.facilityType() == FacilityType.SPORTS_CENTER
                          || f.facilityType() == FacilityType.LIBRARY
                          || f.facilityType() == FacilityType.CHILDCARE
                          || f.facilityType() == FacilityType.SCHOOL
                          || f.facilityType() == FacilityType.CULTURE
          )
          .count();

  long trafficFacilities = safeFacilities.stream()
          .filter(f ->
                  f.facilityType() == FacilityType.TRANSIT_HUB
                          || f.facilityType() == FacilityType.PARKING
          )
          .count();

  int budgetScore = base.economy();

  if (r.totalBudget() > 0) {
   if (cost > r.totalBudget()) {
    budgetScore -= 25;
   } else if (cost > r.totalBudget() * 0.8) {
    budgetScore -= 10;
   }
  }

  int plannedTraffic = clamp(
          base.traffic()
                  + (int) Math.min(
                  12,
                  trafficFacilities * 4
          )
  );

  int plannedEnvironment = clamp(
          base.environment()
                  + (int) Math.min(
                  14,
                  environmentFacilities * 5
          )
  );

  int plannedEconomy = clamp(budgetScore);

  int plannedLiving = clamp(
          base.living()
                  + (int) Math.min(
                  10,
                  medicalFacilities * 4
          )
                  + (int) Math.min(
                  10,
                  livingFacilities * 3
          )
  );

  return scores(
          plannedTraffic,
          plannedEnvironment,
          plannedEconomy,
          plannedLiving
  );
 }

 /**
  * 분야별 점수와 종합점수 생성
  */
 private CityScores scores(
         int traffic,
         int environment,
         int economy,
         int living
 ) {
  int total = Math.round(
          (traffic + environment + economy + living) / 4f
  );

  return new CityScores(
          traffic,
          environment,
          economy,
          living,
          total
  );
 }

 /**
  * 기준 거리 초과분에 대한 감점 계산
  *
  * distance가 0 이하인 경우 미수집 값으로 보고 감점하지 않습니다.
  */
 private int distancePenalty(
         double distance,
         double standard,
         int rate
 ) {
  if (distance <= 0) {
   return 0;
  }

  return (int) (
          Math.max(0, distance - standard) * rate
  );
 }

 /**
  * 인구 10만 명당 시설 수를 기준으로 보너스 계산
  */
 private int densityBonus(
         int count,
         long population,
         int rate,
         int max
 ) {
  if (population <= 0 || count <= 0) {
   return 0;
  }

  double perHundredThousand =
          count * 100_000d / population;

  int bonus = (int) Math.round(
          perHundredThousand * rate
  );

  return Math.min(max, bonus);
 }

 /**
  * 점수를 0점부터 100점 사이로 제한
  */
 public int clamp(int value) {
  return Math.max(
          0,
          Math.min(100, value)
  );
 }
}
