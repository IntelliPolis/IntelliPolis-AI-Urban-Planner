package com.intellipolis.city.dto;

import com.intellipolis.city.model.Enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CityContracts {
 private CityContracts() {}
 public record MapCoordinate(@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
  @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {
  public boolean valid(){ return latitude!=null&&longitude!=null&&latitude>=-90&&latitude<=90&&longitude>=-180&&longitude<=180; }
 }
 public record CityAnalysisRequest(
  @NotBlank(message="도시명은 공백일 수 없습니다.") String cityName,
  @NotBlank(message="지역명은 공백일 수 없습니다.") String districtName,
  @NotNull @PositiveOrZero(message="인구는 0 이상이어야 합니다.") Long population,
  @NotNull @Positive(message="면적은 0보다 커야 합니다.") Double areaKm2,
  @NotNull @DecimalMin("0") @DecimalMax("100") Double elderlyRatio,
  @NotNull @DecimalMin("0") @DecimalMax("100") Double youthRatio,
  @NotNull @DecimalMin("0") @DecimalMax("100") Double parkAreaRatio,
  @NotNull @PositiveOrZero Integer hospitalCount,@NotNull @PositiveOrZero Integer schoolCount,
  @NotNull @PositiveOrZero Integer transitHubCount,@NotNull @PositiveOrZero Double averageHospitalDistanceKm,
  @NotNull @PositiveOrZero Double averageParkDistanceKm,@NotNull @PositiveOrZero Double averageTransitDistanceKm,
  @NotNull @Size(max=20,message="혼잡 도로는 최대 20개까지 입력할 수 있습니다.") List<@NotBlank String> congestedRoads,
  @NotNull @PositiveOrZero Long totalBudget,
  @NotNull @Size(max=10,message="우선 목표는 최대 10개까지 입력할 수 있습니다.") List<@NotBlank String> priorityGoals,
  @NotNull @Valid MapCoordinate mapCenter,@NotNull List<@Valid MapCoordinate> boundary,
  @Size(max=20,message="후보지는 최대 20개까지 입력할 수 있습니다.") List<@Valid MapCoordinate> candidateSites) {}
 public record CityScores(int traffic,int environment,int economy,int living,int overall) {}
 public record UrbanProblem(String title,String description,Severity severity,String evidence) {}
 public record UrbanSuggestion(String title,String description,String expectedEffect,CostLevel estimatedCostLevel) {}
 public record AgentAnalysis(AnalysisDomain domain,int score,String summary,List<UrbanProblem> problems,List<UrbanSuggestion> suggestions,List<String> warnings) {}
 public record PlannedFacility(String id,String name,FacilityType facilityType,PlanStatus status,Double longitude,Double latitude,Double height,Long estimatedCost,String reason) {}
 public record PlannedRoad(String id,String name,RoadType roadType,PlanStatus status,List<List<Double>> coordinates,String reason) {}
 public record PlannedZone(String id,String name,ZoneType zoneType,PlanStatus status,List<List<Double>> coordinates,String reason) {}
 public record ImplementationPhase(int order,String name,String description) {}
 public record CityPlan(PlanType planType,String name,String summary,String purpose,List<PlannedFacility> facilities,List<PlannedRoad> roads,List<PlannedZone> zones,Long estimatedCost,List<String> benefits,List<String> tradeOffs,List<ImplementationPhase> phases,CityScores expectedScores) {}
 public record CityAnalysisResponse(UUID analysisId,String cityName,String districtName,CityScores currentScores,List<AgentAnalysis> agentAnalyses,List<CityPlan> plans,List<String> warnings,OffsetDateTime createdAt) {}
}
