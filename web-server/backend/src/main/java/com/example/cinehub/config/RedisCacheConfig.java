package com.example.cinehub.config;

import com.example.cinehub.dto.response.PageResponse;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis cache cho cac API DOC phim (mo hinh Cache-Aside).
 *
 * <pre>
 *  request doc -> co trong Redis?  -- co  --> tra ve ngay (khong cham MySQL)
 *                                  -- chua --> doc MySQL replica -> luu vao Redis (TTL) -> tra ve
 *  request ghi (them/sua/xoa phim) -> ghi MySQL primary -> xoa cac cache phim lien quan
 * </pre>
 *
 * Nguyen tac an toan:
 * <ul>
 *   <li>Redis chi la bo dem: Redis chet thi app van chay, moi request doc thang MySQL
 *       ({@link #errorHandler()} nuot loi, Lettuce tu choi lenh ngay khi mat ket noi).</li>
 *   <li>Redis KHONG duoc dua vao /actuator/health (management.health.redis.enabled=false),
 *       neu khong Load Balancer se loai ca 3 backend khi Redis chet.</li>
 *   <li>Cache nam NGOAI transaction (order thap hon TransactionInterceptor): cache hit khong mo
 *       transaction/ket noi DB; lenh xoa cache chay SAU khi transaction ghi da commit.</li>
 * </ul>
 */
@Configuration
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class RedisCacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    // Ten cac vung cache. Key that trong Redis: "cinehub::<ten vung>::<key>"
    public static final String MOVIES_PAGE = "movies:page";         // GET /api/movies (trang chu, tim kiem, loc)
    public static final String MOVIES_TRENDING = "movies:trending"; // GET /api/movies/trending
    public static final String MOVIES_BY_TYPE = "movies:byType";    // GET /api/movies/type
    public static final String MOVIES_BY_GENRE = "movies:byGenre";  // GET /api/movies/genre/{slug}

    public static final String KEY_GENERATOR = "requestAwareKeyGenerator";

    private static final String KEY_PREFIX = "cinehub::";

    @Value("${app.cache.ttl-seconds:300}")
    private long ttlSeconds;

    // ------------------------------------------------------------------
    // 1. Dinh dang luu trong Redis: key la chuoi, value la JSON (doc duoc bang redis-cli)
    // ------------------------------------------------------------------
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> KEY_PREFIX + cacheName + "::")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(redisObjectMapper())));
    }

    /**
     * ObjectMapper RIENG cho Redis (khong dung chung ObjectMapper cua REST API):
     * ghi kem ten lop (@class) de doc nguoc lai dung kieu MovieDTO/PageResponse.
     * Chi cho phep cac lop trong goi DTO cua du an va java.util/java.lang -> tranh lo hong
     * deserialization khi du lieu trong Redis bi ke xau sua.
     */
    static ObjectMapper redisObjectMapper() {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.cinehub.dto.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());                        // LocalDate releaseDate
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);       // "2024-05-01" thay vi [2024,5,1]
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);    // DTO them/bot field van doc duoc
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        // PageResponse khong co constructor rong -> chi cho Jackson dung constructor co tham so
        mapper.addMixIn(PageResponse.class, PageResponseMixin.class);
        return mapper;
    }

    /** Khong sua PageResponse: dung mix-in de Jackson biet tao doi tuong tu JSON trong Redis. */
    abstract static class PageResponseMixin {
        @JsonCreator
        PageResponseMixin(@JsonProperty("content") List<?> content,
                          @JsonProperty("currentPage") int currentPage,
                          @JsonProperty("totalPages") int totalPages,
                          @JsonProperty("totalElements") long totalElements,
                          @JsonProperty("last") boolean isLast) {
        }
    }

    // ------------------------------------------------------------------
    // 2. Cache manager: xoa cache bang SCAN (khong dung KEYS chan Redis),
    //    va hoan lenh put/evict den khi transaction bao quanh (neu co) commit xong
    // ------------------------------------------------------------------
    @Bean
    public RedisCacheManagerBuilderCustomizer cinehubCacheManagerCustomizer(RedisConnectionFactory connectionFactory) {
        return builder -> builder
                .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(100)))
                .transactionAware();
    }

    // ------------------------------------------------------------------
    // 3. Redis chet -> fail nhanh thay vi treo request
    // ------------------------------------------------------------------
    @Bean
    public LettuceClientConfigurationBuilderCustomizer cinehubLettuceCustomizer(
            @Value("${spring.data.redis.connect-timeout:500ms}") Duration connectTimeout) {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)                                               // Redis song lai -> tu ket noi lai
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS) // dang mat ket noi -> bao loi ngay
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .timeoutOptions(TimeoutOptions.enabled())
                .build());
    }

    // ------------------------------------------------------------------
    // 4. Key cache: kem dia chi goc cua request
    // ------------------------------------------------------------------
    /**
     * MediaUrlConverter ghep dia chi request vao posterUrl/backdropUrl (vd http://localhost:8080/uploads/..).
     * Vi vay key phai kem dia chi goc, neu khong JSON tao cho cong 8080 se bi tra cho request vao 8081.
     * Request qua Load Balancer deu co X-Forwarded-Host = localhost:8080 -> web-1/2/3 dung CHUNG mot key.
     */
    @Bean(KEY_GENERATOR)
    public KeyGenerator requestAwareKeyGenerator() {
        return (target, method, params) -> currentBaseUrl() + "|" + method.getName() + "("
                + Arrays.stream(params).map(RedisCacheConfig::keyPart).collect(Collectors.joining(",")) + ")";
    }

    private static String currentBaseUrl() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            return "no-request";
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
    }

    private static String keyPart(Object param) {
        if (param == null) {
            return "_";
        }
        if (param instanceof Pageable pageable) {
            return "page=" + pageable.getPageNumber() + ";size=" + pageable.getPageSize()
                    + ";sort=" + pageable.getSort().toString().replace(" ", "");
        }
        return param.toString();
    }

    // ------------------------------------------------------------------
    // 5. Loi Redis khong bao gio lam hong request
    // ------------------------------------------------------------------
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                // Coi nhu cache miss -> Spring goi tiep method doc MySQL
                log.warn("Redis GET loi ({}), doc thang MySQL: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Redis PUT loi ({}), bo qua: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.error("Redis EVICT loi ({}): du lieu cu co the con den khi het TTL: {}",
                        cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.error("Redis CLEAR loi ({}): du lieu cu co the con den khi het TTL: {}",
                        cache.getName(), e.getMessage());
            }
        };
    }
}
