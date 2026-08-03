package com.solo.lock.domain.redis.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RedisLockRepository {
    private final StringRedisTemplate redisTemplate;

    public String tryLock(Long lectureId) {
        String key = "lock:lecture:"+lectureId;
        String token = UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(key, token, Duration.ofSeconds(3));
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public void unlock(Long lectureId, String token) {
        String key = "lock:lecture:"+lectureId;
        String lua = "if redis.call('get', KEYS[1]) == ARGV[1] then "
                + "return redis.call('del', KEYS[1]) else return 0 end";

        redisTemplate.execute(new DefaultRedisScript<>(lua, Long.class),
                List.of(key), token);
    }
}
