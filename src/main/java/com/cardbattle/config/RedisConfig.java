package com.cardbattle.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory f) {
        RedisTemplate<String, Object> t = new RedisTemplate<>();
        t.setConnectionFactory(f);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        Jackson2JsonRedisSerializer<Object> json = new Jackson2JsonRedisSerializer<>(Object.class);
        json.setObjectMapper(om);
        t.setKeySerializer(new StringRedisSerializer());
        t.setValueSerializer(json);
        t.setHashKeySerializer(new StringRedisSerializer());
        t.setHashValueSerializer(json);
        t.afterPropertiesSet();
        return t;
    }
}
