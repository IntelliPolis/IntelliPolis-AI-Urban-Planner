package com.intellipolis.city.controller;
import com.intellipolis.city.dto.CityContracts.*;
import com.intellipolis.city.model.Enums.PlanType;
import com.intellipolis.city.service.CityAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;
@RestController
@RequestMapping("/api")
public class CityController {
 private final CityAnalysisService service;
 public CityController(CityAnalysisService service){this.service=service;}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP","service","IntelliPolis Backend");}
 @GetMapping("/cities/sample") public CityAnalysisRequest sample(){return service.sample();}
 @PostMapping("/city-analyses") @ResponseStatus(HttpStatus.CREATED) public CityAnalysisResponse create(@Valid @RequestBody CityAnalysisRequest request){return service.create(request);}
 @GetMapping("/city-analyses/{id}") public CityAnalysisResponse get(@PathVariable UUID id){return service.get(id);}
 @PostMapping("/city-analyses/{id}/plans/{type}/evaluate") public CityAnalysisResponse evaluate(@PathVariable UUID id,@PathVariable PlanType type,@RequestBody CityPlan plan){return service.evaluate(id,type,plan);}
 @PostMapping("/city-analyses/{id}/plans/{type}/explain") public Map<String,String> explain(@PathVariable UUID id,@PathVariable PlanType type){return service.explain(id,type);}
}
