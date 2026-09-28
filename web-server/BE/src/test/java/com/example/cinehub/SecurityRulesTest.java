package com.example.cinehub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiem tra bang phan quyen trong SecurityConfig.
 * Day la cho de sai nhat: chi can thieu mot dong requestMatchers la
 * trang chu cua FE tra 401, hoac nguoc lai USER xoa duoc phim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRulesTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Load balancer goi /actuator/health duoc khi chua dang nhap")
    void healthCheckIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Response luon co header X-Instance-Id de biet request vao instance nao")
    void responseCarriesInstanceIdHeader() throws Exception {
        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Instance-Id", "test"));
    }

    @Test
    @DisplayName("Cac API trang chu doc duoc khi chua dang nhap")
    void publicBrowsingEndpoints() throws Exception {
        mockMvc.perform(get("/api/movies")).andExpect(status().isOk());
        mockMvc.perform(get("/api/movies/trending")).andExpect(status().isOk());
        mockMvc.perform(get("/api/movies/top-rated")).andExpect(status().isOk());
        mockMvc.perform(get("/api/movies/type").param("movieType", "anime")).andExpect(status().isOk());
        mockMvc.perform(get("/api/movies/genre/hanh-dong")).andExpect(status().isOk());
        mockMvc.perform(get("/api/genres")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Chua dang nhap goi API can quyen -> 401 kem JSON, khong phai trang trang")
    void anonymousGetsJson401() throws Exception {
        mockMvc.perform(get("/api/favorites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    @DisplayName("USER thuong khong duoc tao/xoa phim -> 403 kem JSON")
    void userCannotManageMovies() throws Exception {
        mockMvc.perform(post("/api/movies")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    @DisplayName("USER thuong khong xem duoc danh sach toan bo user")
    void userCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/movies/all")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Enum sai trong query param -> 400 chu khong phai 500")
    void invalidEnumParamGives400() throws Exception {
        mockMvc.perform(get("/api/movies").param("movieType", "khong-ton-tai"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
