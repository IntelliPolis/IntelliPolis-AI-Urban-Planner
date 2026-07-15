package com.intellipolis.spatial;

import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/spatial")
public class SpatialDataController {
    private final SpatialDataService service;
    public SpatialDataController(SpatialDataService service) { this.service = service; }

    @GetMapping("/candidates")
    SpatialDataService.SpatialCandidates candidates(@RequestParam @NotBlank String city,
            @RequestParam @NotBlank String district) {
        return service.candidates(city, district);
    }

    @GetMapping("/boundary")
    SpatialDataService.DistrictBoundary boundary(@RequestParam @NotBlank String city,
            @RequestParam @NotBlank String district) {
        return service.boundary(city, district);
    }
}
