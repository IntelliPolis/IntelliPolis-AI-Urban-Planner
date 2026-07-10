package com.intellipolis.score;

import com.intellipolis.city.dto.CityContracts.*;
import com.intellipolis.city.model.Enums.FacilityType;
import org.springframework.stereotype.Component;
import java.util.List;

/** 프로젝트 비교용 설명 가능한 휴리스틱이며 실제 도시계획 평가 모델이 아닙니다. */
@Component
public class CityScoreCalculator {
 public CityScores current(CityAnalysisRequest r){
  int traffic=clamp(80-(int)(r.averageTransitDistanceKm()*12)+Math.min(15,r.transitHubCount()*2)-r.congestedRoads().size()*4);
  int environment=clamp((int)(45+r.parkAreaRatio()*3-r.averageParkDistanceKm()*10));
  int living=clamp(45+Math.min(18,r.hospitalCount()*2)+Math.min(18,r.schoolCount())-(int)(r.averageHospitalDistanceKm()*8)-(r.elderlyRatio()>25?5:0));
  int economy=clamp(75+(r.totalBudget()>0?5:0));
  return scores(traffic,environment,economy,living);
 }
 public CityScores planned(CityAnalysisRequest r,List<PlannedFacility> facilities,long cost){
  CityScores b=current(r);
  long parks=facilities.stream().filter(f->f.facilityType()==FacilityType.PARK).count();
  long hospitals=facilities.stream().filter(f->f.facilityType()==FacilityType.HOSPITAL).count();
  long transit=facilities.stream().filter(f->f.facilityType()==FacilityType.TRANSIT_HUB).count();
  int economy=clamp(b.economy()-(cost>r.totalBudget()?25:0)-(int)Math.min(15,facilities.stream().filter(f->f.estimatedCost()!=null&&f.estimatedCost()>r.totalBudget()/3).count()*5));
  return scores(clamp(b.traffic()+(int)transit*6),clamp(b.environment()+(int)parks*7),economy,clamp(b.living()+(int)hospitals*7));
 }
 private CityScores scores(int t,int e,int c,int l){ return new CityScores(t,e,c,l,Math.round((t+e+c+l)/4f)); }
 public int clamp(int n){ return Math.max(0,Math.min(100,n)); }
}
