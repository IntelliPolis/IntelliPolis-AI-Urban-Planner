package com.intellipolis.urbandata;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class UrbanDataServiceTest {
 @Test void readsRealDistrictCatalog(){var service=new UrbanDataService("data/raw");var regions=service.regions();assertEquals(java.util.List.of("서울특별시","부산광역시","인천광역시","대구광역시","대전광역시","광주광역시","울산광역시"),new java.util.ArrayList<>(regions.keySet()));assertTrue(regions.get("서울특별시").contains("강남구"));assertTrue(regions.get("광주광역시").contains("광산구"));var summary=service.summary("서울특별시","강남구");assertTrue(summary.businessCount()>0);assertTrue(summary.parkCount()>0);assertTrue(summary.schoolCount()>0);assertEquals(552830,summary.population());assertEquals(17.65,summary.elderlyRatio(),.01);assertTrue(summary.youthRatio()>10);assertNull(summary.busStopCount());assertTrue(summary.budgetByCategory().get("교통및물류")<100_000_000_000L);}
}
