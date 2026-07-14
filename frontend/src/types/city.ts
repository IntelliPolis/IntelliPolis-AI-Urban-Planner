export type PlanType="BALANCED"|"ECO_FOCUSED"|"COST_EFFECTIVE";
export type AnalysisDomain="TRAFFIC"|"ENVIRONMENT"|"ECONOMY"|"LIVING";
export type Severity="LOW"|"MEDIUM"|"HIGH";
export type CostLevel=Severity;
export type PlanStatus="EXISTING"|"PROPOSED";
export type FacilityType="HOSPITAL"|"PARK"|"SCHOOL"|"TRANSIT_HUB"|"CULTURE"|"PUBLIC_SERVICE";
export type RoadType="ROAD"|"BUS"|"BRT"|"SUBWAY"|"PEDESTRIAN"|"BICYCLE";
export type RoadImprovementType="SIGNAL_OPTIMIZATION"|"LANE_OPERATION"|"PUBLIC_TRANSIT"|"PEDESTRIAN_SAFETY"|"DEMAND_MANAGEMENT"|"EXPANSION_REVIEW"|"NEW_ROAD_REVIEW";
export type FeasibilityLevel="HIGH"|"MEDIUM"|"LOW";
export type ZoneType="MIXED_USE"|"RESIDENTIAL"|"COMMERCIAL"|"GREEN"|"REDEVELOPMENT";
export interface MapCenter { latitude:number; longitude:number }
export interface RoadObservation { linkId:string; roadName:string; speedKmh:number; volume:number; intersectionName:string|null; queueLength:number|null; pedestrianCount:number|null; coordinates:number[][] }
export interface CityAnalysisRequest { cityName:string; districtName:string; population:number; areaKm2:number; elderlyRatio:number; youthRatio:number; parkAreaRatio:number; hospitalCount:number; schoolCount:number; transitHubCount:number; averageHospitalDistanceKm:number; averageParkDistanceKm:number; averageTransitDistanceKm:number; congestedRoads:string[]; totalBudget:number; priorityGoals:string[]; mapCenter:MapCenter; boundary:MapCenter[]; candidateSites:MapCenter[]; roadObservations:RoadObservation[] }
export interface CityScores { traffic:number; environment:number; economy:number; living:number; overall:number }
export interface UrbanProblem { title:string; description:string; severity:Severity; evidence:string }
export interface UrbanSuggestion { title:string; description:string; expectedEffect:string; estimatedCostLevel:CostLevel }
export interface AgentAnalysis { domain:AnalysisDomain; score:number; summary:string; problems:UrbanProblem[]; suggestions:UrbanSuggestion[]; warnings:string[] }
export interface PlannedFacility { id:string; name:string; facilityType:FacilityType; status:PlanStatus; longitude:number|null; latitude:number|null; height:number|null; estimatedCost:number|null; reason:string }
export interface PlannedRoad { id:string; name:string; roadType:RoadType; status:PlanStatus; coordinates:number[][]; reason:string; improvementType:RoadImprovementType; feasibility:FeasibilityLevel; evidence:string; landImpact:string; requiredStudies:string[] }
export interface PlannedZone { id:string; name:string; zoneType:ZoneType; status:PlanStatus; coordinates:number[][]; reason:string }
export interface ImplementationPhase { order:number; name:string; description:string }
export interface CityPlan { planType:PlanType; name:string; summary:string; purpose:string; facilities:PlannedFacility[]; roads:PlannedRoad[]; zones:PlannedZone[]; estimatedCost:number|null; benefits:string[]; tradeOffs:string[]; phases:ImplementationPhase[]; expectedScores:CityScores|null }
export interface CityAnalysisResponse { analysisId:string; cityName:string; districtName:string; currentScores:CityScores|null; agentAnalyses:AgentAnalysis[]; plans:CityPlan[]; warnings:string[]; createdAt:string }
