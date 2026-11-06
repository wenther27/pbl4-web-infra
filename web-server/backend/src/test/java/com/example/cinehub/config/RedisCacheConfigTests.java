package com.example.cinehub.config;

import com.example.cinehub.constant.Movie.MovieStatus;
import com.example.cinehub.constant.Movie.MovieType;
import com.example.cinehub.dto.response.GenreDTO;
import com.example.cinehub.dto.response.MovieDTO;
import com.example.cinehub.dto.response.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kiem tra du lieu ghi vao Redis doc nguoc lai dung kieu, va key cache tach dung theo request. */
class RedisCacheConfigTests {

    private final GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(RedisCacheConfig.redisObjectMapper());

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void pageResponseOfMoviesSurvivesRedisRoundTrip() {
        PageResponse<MovieDTO> page = new PageResponse<>(new ArrayList<>(List.of(sampleMovie())), 0, 3, 25L, false);

        byte[] json = serializer.serialize(page);
        Object restored = serializer.deserialize(json);

        assertThat(restored).isInstanceOf(PageResponse.class);
        PageResponse<?> result = (PageResponse<?>) restored;
        assertThat(result.getCurrentPage()).isZero();
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getTotalElements()).isEqualTo(25L);
        assertThat(result.isLast()).isFalse();
        assertThat(result.getContent()).hasSize(1);
        assertMovie((MovieDTO) result.getContent().get(0));
    }

    @Test
    void movieListSurvivesRedisRoundTrip() {
        List<MovieDTO> movies = new ArrayList<>(List.of(sampleMovie()));

        Object restored = serializer.deserialize(serializer.serialize(movies));

        assertThat(restored).isInstanceOf(List.class);
        List<?> result = (List<?>) restored;
        assertThat(result).hasSize(1);
        assertMovie((MovieDTO) result.get(0));
    }

    @Test
    void dateIsStoredAsReadableText() {
        String json = new String(serializer.serialize(sampleMovie()), StandardCharsets.UTF_8);

        assertThat(json).contains("\"releaseDate\":\"2024-05-01\"");
    }

    @Test
    void classesOutsideAllowListAreRejected() {
        // Du lieu trong Redis bi sua de tro toi lop la -> khong duoc phep tao doi tuong
        byte[] malicious = "{\"@class\":\"org.springframework.context.support.ClassPathXmlApplicationContext\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(malicious)).isInstanceOf(SerializationException.class);
    }

    @Test
    void cacheKeyContainsBaseUrlMethodAndParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/movies");
        request.setServerName("localhost");
        request.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        KeyGenerator keyGenerator = new RedisCacheConfig().requestAwareKeyGenerator();
        Method method = getClass().getDeclaredMethod("cacheKeyContainsBaseUrlMethodAndParameters");
        Object key = keyGenerator.generate(this, method,
                null, MovieType.movie, null, PageRequest.of(0, 10, Sort.by("id").ascending()));

        assertThat(key).isEqualTo("http://localhost:8080|cacheKeyContainsBaseUrlMethodAndParameters("
                + "_,movie,_,page=0;size=10;sort=id:ASC)");
    }

    @Test
    void cacheKeyDiffersPerHostBecauseMediaUrlsAreAbsolute() throws Exception {
        KeyGenerator keyGenerator = new RedisCacheConfig().requestAwareKeyGenerator();
        Method method = getClass().getDeclaredMethod("cacheKeyDiffersPerHostBecauseMediaUrlsAreAbsolute");

        MockHttpServletRequest viaLoadBalancer = new MockHttpServletRequest("GET", "/api/movies/trending");
        viaLoadBalancer.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(viaLoadBalancer));
        Object lbKey = keyGenerator.generate(this, method);

        MockHttpServletRequest direct = new MockHttpServletRequest("GET", "/api/movies/trending");
        direct.setServerPort(8081);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(direct));
        Object directKey = keyGenerator.generate(this, method);

        assertThat(lbKey).isNotEqualTo(directKey);
    }

    private static MovieDTO sampleMovie() {
        GenreDTO genre = new GenreDTO();
        genre.setId(1);
        genre.setName("Hành động");
        genre.setSlug("hanh-dong");

        MovieDTO movie = new MovieDTO();
        movie.setId(7L);
        movie.setTitle("Phim mẫu");
        movie.setDescription("Mô tả");
        movie.setPosterUrl("http://localhost:8080/uploads/images/poster.jpg");
        movie.setReleaseDate(LocalDate.of(2024, 5, 1));
        movie.setDuration(120);
        movie.setRating(8.5);
        movie.setIsTrending(true);
        movie.setIsTopRated(false);
        movie.setMovieType(MovieType.movie);
        movie.setStatus(MovieStatus.values()[0]);
        movie.setGenres(Set.of(genre));
        return movie;
    }

    private static void assertMovie(MovieDTO movie) {
        assertThat(movie.getId()).isEqualTo(7L);
        assertThat(movie.getTitle()).isEqualTo("Phim mẫu");
        assertThat(movie.getPosterUrl()).isEqualTo("http://localhost:8080/uploads/images/poster.jpg");
        assertThat(movie.getReleaseDate()).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(movie.getRating()).isEqualTo(8.5);
        assertThat(movie.getIsTrending()).isTrue();
        assertThat(movie.getMovieType()).isEqualTo(MovieType.movie);
        assertThat(movie.getStatus()).isEqualTo(MovieStatus.values()[0]);
        assertThat(movie.getGenres()).hasSize(1);
        assertThat(movie.getGenres().iterator().next().getSlug()).isEqualTo("hanh-dong");
    }
}
