package com.intellipolis.urbandata;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/urban-data")
public class UrbanDataController {
 private final UrbanDataService service;
 public UrbanDataController(UrbanDataService service){this.service=service;}
 @GetMapping("/regions") public Map<String,List<String>> regions(){return service.regions();}
 @GetMapping("/summary") public UrbanDataService.DistrictSummary summary(@RequestParam String city,@RequestParam String district){return service.summary(city,district);}
}
