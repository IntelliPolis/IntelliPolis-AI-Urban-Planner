package com.intellipolis.vworld;

import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;

@Validated @RestController @RequestMapping("/api/vworld")
public class VWorldController {
 private final VWorldService service;
 public VWorldController(VWorldService service){this.service=service;}
 @GetMapping("/status") public Map<String,Object> status(){return service.status();}
 @GetMapping(value="/tiles/{z}/{y}/{x}.png",produces=MediaType.IMAGE_PNG_VALUE) public byte[] tile(@PathVariable int z,@PathVariable int y,@PathVariable int x){return service.tile(z,y,x);}
}
