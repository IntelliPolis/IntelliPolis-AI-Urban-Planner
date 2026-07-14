package com.intellipolis.urbandata;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class UrbanDataServiceTest {
 @Test void readsAllBusanDistricts(){var service=new UrbanDataService("data/raw");var regions=service.regions();assertEquals(java.util.List.of("부산광역시"),new java.util.ArrayList<>(regions.keySet()));assertEquals(16,regions.get("부산광역시").size());for(String district:regions.get("부산광역시")){var summary=service.summary("부산광역시",district);assertTrue(summary.population()>0,district);assertTrue(summary.elderlyRatio()>0,district);assertTrue(summary.youthRatio()>0,district);}var summary=service.summary("부산광역시","해운대구");assertTrue(summary.businessCount()>0);assertTrue(summary.parkCount()>0);assertTrue(summary.schoolCount()>0);assertNull(summary.busStopCount());}
}
