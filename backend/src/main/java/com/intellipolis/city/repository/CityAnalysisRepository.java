package com.intellipolis.city.repository;
import com.intellipolis.city.dto.CityContracts.CityAnalysisResponse;
import com.intellipolis.common.exception.AnalysisNotFoundException;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@Repository
public class CityAnalysisRepository {
 private final ConcurrentHashMap<UUID,CityAnalysisResponse> data=new ConcurrentHashMap<>();
 public CityAnalysisResponse save(CityAnalysisResponse value){data.put(value.analysisId(),value);return value;}
 public CityAnalysisResponse find(UUID id){return java.util.Optional.ofNullable(data.get(id)).orElseThrow(()->new AnalysisNotFoundException(id));}
}
