package com.intellipolis.city.controller;
import tools.jackson.databind.ObjectMapper;
import com.intellipolis.city.service.CityAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="spring.ai.model.chat=none") @AutoConfigureMockMvc
class CityControllerTest {
 @Autowired MockMvc mvc; @Autowired CityAnalysisService service; @Autowired ObjectMapper mapper;
 @Test void healthAndSampleWork() throws Exception {mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));mvc.perform(get("/api/cities/sample")).andExpect(status().isOk()).andExpect(jsonPath("$.districtName").value("가상 해안구"));}
 @Test void analysisUsesFallback() throws Exception {mvc.perform(post("/api/city-analyses").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(service.sample()))).andExpect(status().isCreated()).andExpect(jsonPath("$.plans.length()").value(3)).andExpect(jsonPath("$.warnings[0]").exists());}
 @Test void validationFails() throws Exception {mvc.perform(post("/api/city-analyses").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());}
 @Test void missingIdIs404() throws Exception {mvc.perform(get("/api/city-analyses/"+UUID.randomUUID())).andExpect(status().isNotFound());}
}
