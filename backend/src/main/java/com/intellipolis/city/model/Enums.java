package com.intellipolis.city.model;
public final class Enums {
 private Enums() {}
 public enum AnalysisDomain { TRAFFIC, ENVIRONMENT, ECONOMY, LIVING }
 public enum Severity { LOW, MEDIUM, HIGH }
 public enum CostLevel { LOW, MEDIUM, HIGH }
 public enum PlanType { BALANCED, ECO_FOCUSED, COST_EFFECTIVE }
 public enum PlanStatus { EXISTING, PROPOSED }
 public enum FacilityType { HOSPITAL, PARK, SCHOOL, TRANSIT_HUB, CULTURE, PUBLIC_SERVICE }
 public enum RoadType { ROAD, BUS, BRT, SUBWAY, PEDESTRIAN, BICYCLE }
 public enum RoadImprovementType { SIGNAL_OPTIMIZATION, LANE_OPERATION, PUBLIC_TRANSIT, PEDESTRIAN_SAFETY, DEMAND_MANAGEMENT, EXPANSION_REVIEW, NEW_ROAD_REVIEW }
 public enum FeasibilityLevel { HIGH, MEDIUM, LOW }
 public enum ZoneType { MIXED_USE, RESIDENTIAL, COMMERCIAL, GREEN, REDEVELOPMENT }
}
