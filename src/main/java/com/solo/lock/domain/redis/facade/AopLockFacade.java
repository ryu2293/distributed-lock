package com.solo.lock.domain.redis.facade;

import com.solo.lock.domain.enrollment.service.EnrollmentService;
import com.solo.lock.domain.redis.annotation.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @DistributedLock 어노테이션만으로 분산 락을 거는 파사드.
 * 락 로직(getLock/tryLock/unlock)이 사라지고 비즈니스 호출만 남는다.
 */
@Component
@RequiredArgsConstructor
public class AopLockFacade {

    private final EnrollmentService enrollmentService;

    // 이 한 줄(어노테이션)이 락을 다 처리한다. 몸통은 순수 비즈니스만.
    @DistributedLock(key = "'lock:lecture:' + #lectureId")   // RedissonFacade와 동일한 키 규칙으로 통일
    public void enroll(Long studentId, Long lectureId) {
        enrollmentService.enroll(studentId, lectureId);   // @Transactional 은 별도 빈 → 락이 트랜잭션 바깥
    }
}
