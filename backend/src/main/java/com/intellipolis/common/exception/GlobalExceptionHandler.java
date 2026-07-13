package com.intellipolis.common.exception;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.OffsetDateTime;
import java.util.Map;
import com.intellipolis.vworld.VWorldException;
import com.intellipolis.urbandata.UrbanDataException;
import com.intellipolis.external.ExternalDataException;
import com.intellipolis.traffic.TrafficDataException;
import com.intellipolis.spatial.SpatialDataException;
@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException e,HttpServletRequest r){String m=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getDefaultMessage()).orElse("입력값이 올바르지 않습니다.");return response(400,"Bad Request",m,r);}
 @ExceptionHandler({AnalysisNotFoundException.class}) ResponseEntity<Map<String,Object>> notFound(RuntimeException e,HttpServletRequest r){return response(404,"Not Found",e.getMessage(),r);}
 @ExceptionHandler(VWorldException.class) ResponseEntity<Map<String,Object>> vworld(VWorldException e,HttpServletRequest r){return response(503,"Service Unavailable",e.getMessage(),r);}
 @ExceptionHandler(UrbanDataException.class) ResponseEntity<Map<String,Object>> urbanData(UrbanDataException e,HttpServletRequest r){return response(503,"Service Unavailable",e.getMessage(),r);}
 @ExceptionHandler(ExternalDataException.class) ResponseEntity<Map<String,Object>> externalData(ExternalDataException e,HttpServletRequest r){return response(503,"Service Unavailable",e.getMessage(),r);}
 @ExceptionHandler(TrafficDataException.class) ResponseEntity<Map<String,Object>> traffic(TrafficDataException e,HttpServletRequest r){return response(503,"Service Unavailable",e.getMessage(),r);}
 @ExceptionHandler(SpatialDataException.class) ResponseEntity<Map<String,Object>> spatial(SpatialDataException e,HttpServletRequest r){return response(503,"Service Unavailable",e.getMessage(),r);}
 @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<Map<String,Object>> bad(Exception e,HttpServletRequest r){return response(400,"Bad Request","요청 값 또는 계획안 타입이 올바르지 않습니다.",r);}
 @ExceptionHandler(Exception.class) ResponseEntity<Map<String,Object>> unknown(Exception e,HttpServletRequest r){return response(500,"Internal Server Error","예상하지 못한 오류가 발생했습니다.",r);}
 private ResponseEntity<Map<String,Object>> response(int status,String error,String message,HttpServletRequest req){return ResponseEntity.status(status).body(Map.of("timestamp",OffsetDateTime.now().toString(),"status",status,"error",error,"message",message,"path",req.getRequestURI()));}
}
