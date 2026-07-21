package com.solo.lock.domain.enrollment.facade;

import com.solo.lock.domain.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentFacade {

    private final EnrollmentService enrollmentService;

    private final int MAX_RETRY = 100;

    public synchronized void enrollFacade(Long studentId, Long lectureId) {
        enrollmentService.enroll(studentId, lectureId);
    }

    public void optimisticEnrollFacade(Long studentId, Long lectureId) {
        for(int i=0; i<MAX_RETRY; i++) {
            try {
                enrollmentService.optimisticEnroll(studentId, lectureId);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if(i == MAX_RETRY-1) throw e;
            }
        }
    }
}
