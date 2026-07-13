package com.intellipolis.spatial;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
            @RequestParam @NotBlank String district,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude) {
        return service.candidates(city, district, longitude, latitude);
    }

    @GetMapping("/boundary")
    SpatialDataService.DistrictBoundary boundary(@RequestParam @NotBlank String city,
            @RequestParam @NotBlank String district) {
        return service.boundary(city, district);
    }
}
