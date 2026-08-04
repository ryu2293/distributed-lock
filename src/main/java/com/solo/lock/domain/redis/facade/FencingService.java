package com.solo.lock.domain.redis.facade;

import com.solo.lock.domain.lecture.repository.LectureRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Fencing token 데모용 서비스.
 * 락을 잡고 → 증가 토큰(INCR)을 발급받아 → 자원에 쓴다.
 * pauseMillis 로 "긴 GC/멈춤"을 흉내내 락 만료(좀비 홀더) 상황을 재현한다.
 */
@Service
@RequiredArgsConstructor
public class FencingService {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;   // 토큰 발급용 (INCR)
    private final LectureRepository lectureRepository;

    /**
     * @param useFence   true=fencing 가드 사용(GREEN), false=무조건 덮어쓰기(RED)
     * @param pauseMillis 임계구역에서 멈추는 시간(락 만료 유발용)
     * @param leaseSeconds 락 점유 시간(TTL). 이 시간 지나면 락 자동 만료
     * @return 쓰기 쿼리가 반영한 row 수 (1=성공, 0=fencing에 의해 거부)
     */
    @Transactional   // @Modifying 쓰기 쿼리에 트랜잭션 필요 (데모라 락+트랜잭션 한 메서드에 둠)
    public int doWork(Long lectureId, int value, boolean useFence,
                      long pauseMillis, long leaseSeconds) throws InterruptedException {

        RLock lock = redissonClient.getLock("lock:fence:" + lectureId);
        // leaseTime 명시 → watchdog OFF → 이 시간 지나면 락이 확실히 만료됨
        boolean acquired = lock.tryLock(5, leaseSeconds, TimeUnit.SECONDS);
        if (!acquired) throw new RuntimeException("락 획득 실패");

        // 락 획득할 때마다 증가 토큰 발급 (INCR은 원자적 → 유일 증가값 보장)
        long token = redisTemplate.opsForValue().increment("fence:lecture:" + lectureId);

        try {
            Thread.sleep(pauseMillis);   // 멈춤 흉내 → 이 사이 락이 만료될 수 있음(좀비화)
            if (useFence) {
                return lectureRepository.writeWithFence(lectureId, value, token);  // 토큰 가드
            } else {
                return lectureRepository.writeNoFence(lectureId, value);           // 무조건 덮어씀
            }
        } finally {
            // 내 락이 이미 만료돼 남이 가졌을 수 있으므로, 내가 들고 있을 때만 해제
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
