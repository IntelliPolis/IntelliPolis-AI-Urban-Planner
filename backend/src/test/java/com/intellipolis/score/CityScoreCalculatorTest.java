package com.intellipolis.score;
import com.intellipolis.city.dto.CityContracts.*;
import com.intellipolis.city.model.Enums.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class CityScoreCalculatorTest {
 private final CityScoreCalculator c=new CityScoreCalculator();
 private CityAnalysisRequest request(){return new CityAnalysisRequest("부산광역시","가상 해안구",320000L,48.5,22.4,14.2,8.1,12,34,8,2.3,1.8,1.1,List.of("중앙로"),100L,List.of("환경"),new MapCoordinate(35.16,129.16),List.of(),List.of(),List.of());}
 private PlannedFacility f(FacilityType t,long cost){return new PlannedFacility("id","시설",t,PlanStatus.PROPOSED,129.1,35.1,0d,cost,"검토");}
 @Test void currentScoresStayInRange(){var s=c.current(request());assertThat(List.of(s.traffic(),s.environment(),s.economy(),s.living(),s.overall())).allMatch(x->x>=0&&x<=100);}
 @Test void currentScoreIsCalculated(){assertThat(c.current(request()).overall()).isPositive();}
 @Test void overBudgetLowersEconomy(){assertThat(c.planned(request(),List.of(),101).economy()).isLessThan(c.planned(request(),List.of(),100).economy());}
 @Test void parkRaisesEnvironment(){assertThat(c.planned(request(),List.of(f(FacilityType.PARK,10)),10).environment()).isGreaterThan(c.current(request()).environment());}
 @Test void hospitalRaisesLiving(){assertThat(c.planned(request(),List.of(f(FacilityType.HOSPITAL,10)),10).living()).isGreaterThan(c.current(request()).living());}
 @Test void publicServiceRaisesLiving(){assertThat(c.planned(request(),List.of(f(FacilityType.PUBLIC_SERVICE,10)),10).living()).isGreaterThan(c.current(request()).living());}
}
