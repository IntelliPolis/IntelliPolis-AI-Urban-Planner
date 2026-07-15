package com.intellipolis.urbandata;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class UrbanDataServiceTest {
 @Test void readsAllBusanDistricts(){var service=new UrbanDataService("data/raw");var regions=service.regions();assertEquals(java.util.List.of("부산광역시"),new java.util.ArrayList<>(regions.keySet()));assertEquals(16,regions.get("부산광역시").size());for(String district:regions.get("부산광역시")){var summary=service.summary("부산광역시",district);assertTrue(summary.population()>0,district);assertTrue(summary.elderlyRatio()>0,district);assertTrue(summary.youthRatio()>0,district);}var summary=service.summary("부산광역시","해운대구");assertTrue(summary.businessCount()>0);assertTrue(summary.parkCount()>0);assertTrue(summary.schoolCount()>0);assertNull(summary.busStopCount());}
 @Test void brokenOptionalBusinessFileDoesNotBreakRegions(@TempDir Path dir)throws Exception{Files.writeString(dir.resolve("소상공인시장진흥공단_상가(상권)정보_부산_bad.csv"),"시도명,시군구명\n\"깨진 행");var service=new UrbanDataService(dir.toString());assertEquals(16,service.regions().get("부산광역시").size());var summary=service.summary("부산광역시","강서구");assertTrue(summary.warnings().stream().anyMatch(x->x.contains("상권 파일")));}
}
