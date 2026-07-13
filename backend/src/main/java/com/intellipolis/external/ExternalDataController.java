package com.intellipolis.external;import java.util.*;import org.springframework.web.bind.annotation.*;import tools.jackson.databind.JsonNode;
@RestController @RequestMapping("/api/external-data") public class ExternalDataController{
 private final ExternalDataService service;public ExternalDataController(ExternalDataService service){this.service=service;}
 @GetMapping("/status")Map<String,Boolean> status(){return service.status();}
 @GetMapping("/hospitals")JsonNode hospitals(@RequestParam String sidoCode,@RequestParam String districtCode){return service.hospitals(sidoCode,districtCode);}
 @GetMapping("/air-quality")JsonNode air(@RequestParam String sido){return service.air(sido);}
 @GetMapping("/schools")JsonNode schools(@RequestParam String officeCode,@RequestParam String district){return service.schools(officeCode,district);}
}
