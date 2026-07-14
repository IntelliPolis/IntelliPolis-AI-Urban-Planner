package com.intellipolis.urbandata;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class UrbanDataServiceTest {
 @Test void readsBusanDistrictCatalog(){var service=new UrbanDataService("data/raw");var regions=service.regions();assertEquals(java.util.List.of("부산광역시"),new java.util.ArrayList<>(regions.keySet()));assertTrue(regions.get("부산광역시").contains("해운대구"));var summary=service.summary("부산광역시","해운대구");assertTrue(summary.businessCount()>0);assertTrue(summary.parkCount()>0);assertTrue(summary.schoolCount()>0);assertTrue(summary.population()>0);assertTrue(summary.elderlyRatio()>0);assertTrue(summary.youthRatio()>0);assertNull(summary.busStopCount());}
}
