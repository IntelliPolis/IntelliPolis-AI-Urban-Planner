package com.intellipolis.common.exception;
import java.util.UUID;
public class AnalysisNotFoundException extends RuntimeException {
 public AnalysisNotFoundException(UUID id){super("도시 분석을 찾을 수 없습니다: "+id);}
}
