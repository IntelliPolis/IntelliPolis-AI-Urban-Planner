package com.intellipolis.score;

import com.intellipolis.city.dto.CityContracts.*;
import com.intellipolis.city.model.Enums.FacilityType;
import org.springframework.stereotype.Component;
import java.util.List;

/** 프로젝트 비교용 설명 가능한 휴리스틱이며 실제 도시계획 평가 모델이 아닙니다. */
@Component
public class CityScoreCalculator {
 public CityScores current(CityAnalysisRequest r){
  int traffic=clamp(68-distancePenalty(r.averageTransitDistanceKm(),.5,12)+densityBonus(r.transitHubCount(),r.population(),2,15)-Math.min(30,r.congestedRoads().size()*3));
  int environment=clamp(45+(int)(r.parkAreaRatio()*3)-distancePenalty(r.averageParkDistanceKm(),.5,10));
  int living=clamp(45+densityBonus(r.hospitalCount(),r.population(),2,18)+densityBonus(r.schoolCount(),r.population(),1,18)-distancePenalty(r.averageHospitalDistanceKm(),1,8)-(r.elderlyRatio()>25?5:0));
  // 경제활동·고용·재정자립도 자료가 없으므로 예산 존재 여부만으로 우수하다고 간주하지 않는다.
  int economy=50;
  return scores(traffic,environment,economy,living);
 }
 public CityScores planned(CityAnalysisRequest r,List<PlannedFacility> facilities,long cost){
  CityScores b=current(r);
  long parks=facilities.stream().filter(f->f.facilityType()==FacilityType.PARK).count();
  long hospitals=facilities.stream().filter(f->f.facilityType()==FacilityType.HOSPITAL).count();
  long transit=facilities.stream().filter(f->f.facilityType()==FacilityType.TRANSIT_HUB).count();
  long publicServices=facilities.stream().filter(f->f.facilityType()==FacilityType.PUBLIC_SERVICE).count();
  int economy=clamp(b.economy()-(cost>r.totalBudget()?25:0)-(int)Math.min(15,facilities.stream().filter(f->f.estimatedCost()!=null&&f.estimatedCost()>r.totalBudget()/3).count()*5));
  return scores(clamp(b.traffic()+(int)transit*6),clamp(b.environment()+(int)parks*7),economy,clamp(b.living()+(int)hospitals*7+(int)publicServices*4));
 }
 private CityScores scores(int t,int e,int c,int l){ return new CityScores(t,e,c,l,Math.round((t+e+c+l)/4f)); }
 private int distancePenalty(double distance,double standard,int rate){return distance<=0?0:(int)(Math.max(0,distance-standard)*rate);}
 private int densityBonus(int count,long population,int rate,int max){return population<=0?0:Math.min(max,(int)Math.round(count*100_000d/population*rate));}
 public int clamp(int n){ return Math.max(0,Math.min(100,n)); }
}
