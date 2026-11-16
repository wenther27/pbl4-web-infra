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

@Configuration
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class RedisCacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    public static final String MOVIES_PAGE = "movies:page";
    public static final String MOVIES_TRENDING = "movies:trending";
    public static final String MOVIES_BY_TYPE = "movies:byType";
    public static final String MOVIES_BY_GENRE = "movies:byGenre";

    public static final String KEY_GENERATOR = "requestAwareKeyGenerator";

    private static final String KEY_PREFIX = "cinehub::";

    @Value("${app.cache.ttl-seconds:300}")
    private long ttlSeconds;

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

    static ObjectMapper redisObjectMapper() {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.cinehub.dto.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        mapper.addMixIn(PageResponse.class, PageResponseMixin.class);
        return mapper;
    }

    abstract static class PageResponseMixin {
        @JsonCreator
        PageResponseMixin(@JsonProperty("content") List<?> content,
                          @JsonProperty("currentPage") int currentPage,
                          @JsonProperty("totalPages") int totalPages,
                          @JsonProperty("totalElements") long totalElements,
                          @JsonProperty("last") boolean isLast) {
        }
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer cinehubCacheManagerCustomizer(RedisConnectionFactory connectionFactory) {
        return builder -> builder
                .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(100)))
                .transactionAware();
    }

    @Bean
    public LettuceClientConfigurationBuilderCustomizer cinehubLettuceCustomizer(
            @Value("${spring.data.redis.connect-timeout:500ms}") Duration connectTimeout) {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .timeoutOptions(TimeoutOptions.enabled())
                .build());
    }

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

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
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
