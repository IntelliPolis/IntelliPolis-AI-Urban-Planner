package com.intellipolis.urbandata;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class UrbanDataServiceTest {
 @Test void readsRealDistrictCatalog(){var service=new UrbanDataService("data/raw");var regions=service.regions();assertTrue(regions.get("서울특별시").contains("강남구"));assertTrue(regions.get("광주광역시").contains("광산구"));var summary=service.summary("서울특별시","강남구");assertTrue(summary.businessCount()>0);assertTrue(summary.parkCount()>0);assertNull(summary.schoolCount());assertTrue(summary.budgetByCategory().get("교통및물류")<100_000_000_000L);}
}
