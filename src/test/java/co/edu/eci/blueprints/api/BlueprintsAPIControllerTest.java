package co.edu.eci.blueprints.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BlueprintsAPIControllerTest {

    private static final SimpleGrantedAuthority READ = new SimpleGrantedAuthority("SCOPE_blueprints.read");
    private static final SimpleGrantedAuthority WRITE = new SimpleGrantedAuthority("SCOPE_blueprints.write");

    @Autowired
    private MockMvc mvc;

    @Test
    void getByAuthorQueryReturnsBlueprintsAndTotalPoints() throws Exception {
        // Seed data: john/house (6 points) + john/garage (5 points)
        mvc.perform(get("/api/v1/blueprints").param("author", "john").with(jwt().authorities(READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.author").value("john"))
                .andExpect(jsonPath("$.data.totalPoints").value(11))
                .andExpect(jsonPath("$.data.blueprints.length()").value(2));
    }

    @Test
    void getByAuthorQueryReturns404ForUnknownAuthor() throws Exception {
        mvc.perform(get("/api/v1/blueprints").param("author", "nobody").with(jwt().authorities(READ)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void getWithoutAuthorParamStillReturnsAllBlueprints() throws Exception {
        mvc.perform(get("/api/v1/blueprints").with(jwt().authorities(READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getByAuthorQueryWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/blueprints").param("author", "john"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addPointRejectsNegativeCoordinates() throws Exception {
        mvc.perform(put("/api/v1/blueprints/jane/pool/points")
                        .with(jwt().authorities(WRITE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\": -1, \"y\": 5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
