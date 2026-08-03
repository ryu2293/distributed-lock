package com.solo.lock.domain.redis.facade;

import com.solo.lock.domain.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedissonFacade {

    private final RedissonClient redissonClient;
    private final EnrollmentService enrollmentService;

    public void enroll(Long studentId, Long lectureId) throws InterruptedException{
        RLock lock = redissonClient.getLock("lock:lecture:" + lectureId);
        boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);

        if(!acquired) throw new RuntimeException("락 획득 실패!");
        try {
            enrollmentService.enroll(studentId, lectureId);
        } finally {
            if(lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
