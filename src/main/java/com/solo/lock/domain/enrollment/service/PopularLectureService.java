package com.solo.lock.domain.enrollment.service;

import com.solo.lock.domain.enrollment.dto.response.PopularLectureRow;
import com.solo.lock.domain.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 인기 강의 TOP N 집계 결과를 Redis에 캐싱.
 * [RED 단계] 락이 없어서 캐시 미스가 동시에 나면 여러 스레드가 함께 재계산한다(스탬피드).
 */
@Service
@RequiredArgsConstructor
public class PopularLectureService {

    private static final String CACHE_KEY = "cache:popular:lectures";
    private static final int TOP_N = 5;

    private final EnrollmentRepository enrollmentRepository;
    private final StringRedisTemplate redisTemplate;   // 캐시 저장소 (Lettuce 기반)
    private final ObjectMapper objectMapper;           // List <-> JSON (스프링 자동 등록 빈)

    private final AtomicInteger recomputeCount = new AtomicInteger();  // 재계산 횟수 = 스탬피드 측정

    public List<PopularLectureRow> getPopular() {
        // 1. 캐시 조회
        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return deserialize(cached);   // 캐시 히트
        }

        // 2. 캐시 미스 → 재계산 (여기가 스탬피드가 터지는 구간)
        recomputeCount.incrementAndGet();
        sleepExpensive();                 // 무거운 집계 흉내 (재계산 창을 넓혀 스탬피드 관찰 용이)
        List<PopularLectureRow> result =
                enrollmentRepository.findPopular(PageRequest.of(0, TOP_N));   // 상위 5개

        // 3. 캐시에 저장 (짧은 TTL로 만료 순간을 만들기 쉽게)
        redisTemplate.opsForValue()
                .set(CACHE_KEY, objectMapper.writeValueAsString(result), Duration.ofSeconds(3));
        return result;
    }

    public int getRecomputeCount() {
        return recomputeCount.get();
    }

    /** 반복 테스트용: 캐시 삭제 + 카운터 리셋 */
    public void resetCache() {
        redisTemplate.delete(CACHE_KEY);
        recomputeCount.set(0);
    }

    private List<PopularLectureRow> deserialize(String json) {
        // Jackson 3: 예외가 unchecked라 try-catch 불필요
        return objectMapper.readValue(json, new TypeReference<List<PopularLectureRow>>() {});
    }

    private void sleepExpensive() {
        try {
            Thread.sleep(200);   // 비싼 집계 흉내
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("재계산 중 인터럽트", e);
        }
    }
}
