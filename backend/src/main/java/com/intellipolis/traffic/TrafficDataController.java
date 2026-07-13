package com.intellipolis.traffic;
import jakarta.validation.constraints.*;import java.util.Map;import org.springframework.validation.annotation.Validated;import org.springframework.web.bind.annotation.*;
@Validated @RestController @RequestMapping("/api/traffic")
public class TrafficDataController{
 private final TrafficDataService service;public TrafficDataController(TrafficDataService service){this.service=service;}
 @GetMapping("/status")Map<String,Object> status(){return service.status();}
 @GetMapping("/summary")TrafficDataService.TrafficSummary summary(@RequestParam @NotBlank String city,@RequestParam @NotBlank String district,@RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,@RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude){return service.summary(city,district,longitude,latitude);}
}
