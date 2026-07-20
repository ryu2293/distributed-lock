package com.solo.lock.domain.enrollment.facade;

import com.solo.lock.domain.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentFacade {

    private final EnrollmentService enrollmentService;

    public synchronized void enrollFacade(Long studentId, Long lectureId) {
        enrollmentService.enroll(studentId, lectureId);
    }
}
