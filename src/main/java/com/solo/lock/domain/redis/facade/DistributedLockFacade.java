package com.solo.lock.domain.redis.facade;

import com.solo.lock.domain.enrollment.service.EnrollmentService;
import com.solo.lock.domain.redis.repository.RedisLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DistributedLockFacade {
    private final RedisLockRepository redisLockRepository;
    private final EnrollmentService enrollmentService;

    public void enroll(Long studentId, Long lectureId) throws InterruptedException {
        String token;
        long deadline = System.currentTimeMillis() + 5000;
        while((token = redisLockRepository.tryLock(lectureId)) == null) {
            if(System.currentTimeMillis() > deadline)
                throw new RuntimeException("락 획득 실패");
            Thread.sleep(50);
        }
        try {
            enrollmentService.enroll(studentId, lectureId);   // 트랜잭션은 락 안에서
        } finally {
            redisLockRepository.unlock(lectureId, token);          // 반드시 해제
        }
    }
}
