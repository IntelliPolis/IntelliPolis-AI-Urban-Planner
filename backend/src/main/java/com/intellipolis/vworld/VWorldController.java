package com.intellipolis.vworld;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

@Validated @RestController @RequestMapping("/api/vworld")
public class VWorldController {
 private final VWorldService service;
 public VWorldController(VWorldService service){this.service=service;}
 @GetMapping("/status") public Map<String,Object> status(){return service.status();}
 @GetMapping(value="/tiles/{z}/{y}/{x}.png",produces=MediaType.IMAGE_PNG_VALUE) public byte[] tile(@PathVariable int z,@PathVariable int y,@PathVariable int x){return service.tile(z,y,x);}
 @GetMapping("/search") public JsonNode search(@RequestParam @NotBlank String query){return service.search(query);}
}
