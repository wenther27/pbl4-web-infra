package com.example.cinehub;

import com.example.cinehub.constant.User.RoleUser;
import com.example.cinehub.entity.User;
import com.example.cinehub.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Di het mot luot nghiep vu that: dang ky -> dang nhap -> lay token ->
 * admin them the loai/phim -> user yeu thich, binh luan, luu lich su xem.
 * Test nay chay qua ca JwtFilter nen bat duoc loi o toan bo chuoi xu ly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "matkhau123";

    private String adminToken;

    @BeforeEach
    void setUpAdmin() throws Exception {
        // Khong dang ky duoc ADMIN qua API (co y): tao truc tiep bang repository
        if (!userRepository.existsByUsername("admin_test")) {
            User admin = new User();
            admin.setUsername("admin_test");
            admin.setEmail("admin_test@cinehub.com");
            admin.setPassword(passwordEncoder.encode(PASSWORD));
            admin.setRole(RoleUser.ADMIN);
            userRepository.save(admin);
        }
        adminToken = login("admin_test");
    }

    @Test
    @DisplayName("Dang ky luon ra role USER, kho co the tu nang quyen")
    void registerAlwaysCreatesUserRole() throws Exception {
        String body = """
                {"username":"newbie","email":"newbie@cinehub.com","password":"%s"}
                """.formatted(PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                // Response khong duoc chua password, du la ban da ma hoa
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(userRepository.findByUsername("newbie"))
                .get()
                .satisfies(u -> {
                    assertThat(u.getRole()).isEqualTo(RoleUser.USER);
                    // Password phai duoc ma hoa, khong luu tho
                    assertThat(u.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
                    // 2 cot NOT NULL trong bang users
                    assertThat(u.getCreateAt()).isNotNull();
                    assertThat(u.getUpdateAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("Dang ky trung username -> 400 co thong bao, khong phai 500")
    void duplicateUsernameGives400() throws Exception {
        register("trung_ten", "trung1@cinehub.com");

        String body = """
                {"username":"trung_ten","email":"trung2@cinehub.com","password":"%s"}
                """.formatted(PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value("Username đã tồn tại"));
    }

    @Test
    @DisplayName("Dang nhap sai mat khau -> 401 co JSON")
    void wrongPasswordGives401() throws Exception {
        register("sai_pass", "saipass@cinehub.com");

        String body = """
                {"username":"sai_pass","password":"sai-be-het"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Đăng nhập thất bại"));
    }

    @Test
    @DisplayName("Luong day du: admin tao the loai + phim, user yeu thich/binh luan/luu lich su")
    void fullBusinessFlow() throws Exception {
        // --- ADMIN tao the loai ---
        MvcResult genreResult = mockMvc.perform(post("/api/genres")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hành động","slug":"hanh-dong"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        int genreId = json(genreResult).get("id").asInt();

        // Trung slug -> 400 chu khong phai 500 tu DB
        mockMvc.perform(post("/api/genres")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hành động 2","slug":"hanh-dong"}
                                """))
                .andExpect(status().isBadRequest());

        // --- ADMIN tao phim, gan the loai ---
        MvcResult movieResult = mockMvc.perform(post("/api/movies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Inception",
                                  "description":"Kẻ đánh cắp giấc mơ",
                                  "duration":148,
                                  "rating":8.8,
                                  "isTrending":true,
                                  "isTopRated":true,
                                  "movieType":"movie",
                                  "status":"released",
                                  "genreIds":[%d]
                                }
                                """.formatted(genreId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.genres[0].slug").value("hanh-dong"))
                .andReturn();
        long movieId = json(movieResult).get("id").asLong();

        // --- Chi tiet phim: public, tra kem the loai ---
        mockMvc.perform(get("/api/movies/" + movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Inception"))
                .andExpect(jsonPath("$.genres[0].name").value("Hành động"));

        mockMvc.perform(get("/api/movies/999999"))
                .andExpect(status().isNotFound());

        // Phim vua tao co isTrending/isTopRated = true
        mockMvc.perform(get("/api/movies/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Inception"));
        mockMvc.perform(get("/api/movies/top-rated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Inception"));

        // --- ADMIN them tap phim ---
        MvcResult epResult = mockMvc.perform(post("/api/movies/" + movieId + "/episodes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Tập 1","episodeNumber":1,"videoUrl":"https://cdn/x.mp4","duration":24}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long episodeId = json(epResult).get("id").asLong();

        // Trung so tap -> 400
        mockMvc.perform(post("/api/movies/" + movieId + "/episodes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Tập 1 bản khác","episodeNumber":1,"videoUrl":"https://cdn/y.mp4"}
                                """))
                .andExpect(status().isBadRequest());

        // --- USER dang ky va dang nhap ---
        register("user_flow", "userflow@cinehub.com");
        String userToken = login("user_flow");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user_flow"))
                .andExpect(jsonPath("$.role").value("USER"));

        // --- Yeu thich ---
        mockMvc.perform(post("/api/favorites/" + movieId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movieTitle").value("Inception"))
                .andExpect(jsonPath("$.addedAt").exists());

        // Yeu thich lan 2 -> 400
        mockMvc.perform(post("/api/favorites/" + movieId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/favorites").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movieTitle").value("Inception"));

        mockMvc.perform(get("/api/favorites/" + movieId + "/check")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string("true"));

        // --- Lich su xem: goi 2 lan chi tao 1 record (upsert) ---
        String historyBody = """
                {"movieId":%d,"episodeId":%d,"lastPosition":%d}
                """;
        mockMvc.perform(post("/api/history")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(historyBody.formatted(movieId, episodeId, 100)))
                .andExpect(status().isOk());

        MvcResult historyResult = mockMvc.perform(post("/api/history")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(historyBody.formatted(movieId, episodeId, 500)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastPosition").value(500))
                .andExpect(jsonPath("$.episodeNumber").value(1))
                .andReturn();

        mockMvc.perform(get("/api/history").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(json(historyResult).get("movieTitle").asText()).isEqualTo("Inception");

        // Thieu movieId -> 400 (truoc day nhan Map nen se la NPE -> 500)
        mockMvc.perform(post("/api/history")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastPosition":10}
                                """))
                .andExpect(status().isBadRequest());

        // --- Binh luan ---
        MvcResult reviewResult = mockMvc.perform(post("/api/movies/" + movieId + "/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Phim hay","rating":9}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user_flow"))
                .andReturn();
        long reviewId = json(reviewResult).get("id").asLong();

        // rating ngoai khoang 1-10 -> 400
        mockMvc.perform(post("/api/movies/" + movieId + "/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Sai điểm","rating":99}
                                """))
                .andExpect(status().isBadRequest());

        // Binh luan doc duoc khi chua dang nhap
        mockMvc.perform(get("/api/movies/" + movieId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Phim hay"));

        // --- Nguoi khac khong xoa duoc binh luan cua user_flow ---
        register("ke_khac", "kekhac@cinehub.com");
        String otherToken = login("ke_khac");

        mockMvc.perform(delete("/api/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // Chinh chu thi xoa duoc
        mockMvc.perform(delete("/api/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        // --- USER khong xoa duoc phim ---
        mockMvc.perform(delete("/api/movies/" + movieId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // ADMIN xoa duoc. Dung mot phim rieng chua co ai yeu thich/xem do,
        // vi viec xoa phim dang duoc dua vao ON DELETE CASCADE cua MySQL
        // (schema H2 sinh tu entity trong test khong co cascade do).
        MvcResult tempMovie = mockMvc.perform(post("/api/movies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Phim tạm","duration":90,"rating":5.0,
                                 "movieType":"movie","status":"released"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(delete("/api/movies/" + json(tempMovie).get("id").asLong())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Khuon JSON cua PageResponse - khoa hop dong voi FE")
    void pagedResponseShape() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/movies").param("size", "5"))
                .andExpect(status().isOk())
                .andReturn();

        // Luu y: field trong Java ten la isLast, nhung Jackson doc qua getter
        // isLast() nen ten trong JSON la "last". FE phai doc dung ten nay.
        assertThat(json(result).fieldNames()).toIterable()
                .containsExactlyInAnyOrder(
                        "content", "currentPage", "totalPages", "totalElements", "last");
    }

    @Test
    @DisplayName("Token rac -> 401, khong lot vao duoc")
    void garbageTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/favorites").header("Authorization", "Bearer khong-phai-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- helper ----------------

    private void register(String username, String email) throws Exception {
        String body = """
                {"username":"%s","email":"%s","password":"%s"}
                """.formatted(username, email, PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private String login(String username) throws Exception {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(username, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
