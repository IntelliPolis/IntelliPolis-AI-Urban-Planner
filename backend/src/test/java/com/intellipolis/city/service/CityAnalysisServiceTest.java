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
 @Test void invalidCoordinateIsExcluded(){var a=service.create(service.sample());var p=a.plans().get(0);var invalid=new PlannedFacility("x","오류 시설",FacilityType.PARK,PlanStatus.PROPOSED,200d,95d,0d,0L,"좌표 오류");var updated=service.evaluate(a.analysisId(),p.planType(),changed(p,List.of(invalid),List.of()));assertThat(updated.plans().stream().filter(x->x.planType()==p.planType()).findFirst().orElseThrow().facilities()).isEmpty();}
 @Test void polygonIsClosedAutomatically(){var a=service.create(service.sample());var p=a.plans().get(0);var coords=List.of(List.of(129.1,35.1),List.of(129.2,35.1),List.of(129.2,35.2),List.of(129.1,35.2));var zone=new PlannedZone("z","개선 구역",ZoneType.GREEN,PlanStatus.PROPOSED,coords,"검토");var updated=service.evaluate(a.analysisId(),p.planType(),changed(p,List.of(),List.of(zone)));var ring=updated.plans().stream().filter(x->x.planType()==p.planType()).findFirst().orElseThrow().zones().get(0).coordinates();assertThat(ring.getFirst()).isEqualTo(ring.getLast());}
 @Test void eachPlanImprovesItsPrimaryScore(){var a=service.create(service.sample());var plans=a.plans().stream().collect(java.util.stream.Collectors.toMap(CityPlan::planType,p->p));assertThat(plans.get(PlanType.BALANCED).expectedScores().traffic()).isGreaterThan(a.currentScores().traffic());assertThat(plans.get(PlanType.ECO_FOCUSED).expectedScores().environment()).isGreaterThan(a.currentScores().environment());assertThat(plans.get(PlanType.COST_EFFECTIVE).expectedScores().living()).isGreaterThan(a.currentScores().living());}
 @Test void evaluateRejectsMismatchedPlanType(){var a=service.create(service.sample());var eco=a.plans().stream().filter(p->p.planType()==PlanType.ECO_FOCUSED).findFirst().orElseThrow();org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->service.evaluate(a.analysisId(),PlanType.BALANCED,eco));}
}
