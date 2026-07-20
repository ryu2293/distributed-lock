package com.solo.lock.domain.enrollment.repository;

import com.solo.lock.domain.enrollment.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Long, Enrollment> {
}
