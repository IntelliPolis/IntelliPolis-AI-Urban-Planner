package com.intellipolis.vworld;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.ai.model.chat=none","app.vworld.api-key="}) @AutoConfigureMockMvc
class VWorldControllerTest {
 @Autowired MockMvc mvc;
 @Test void reportsMissingConfigurationWithoutExposingKey() throws Exception {mvc.perform(get("/api/vworld/status")).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.apiKey").doesNotExist());}
}
