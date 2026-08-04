package com.solo.lock.domain.enrollment.repository;

import com.solo.lock.domain.enrollment.dto.response.PopularLectureRow;
import com.solo.lock.domain.enrollment.entity.Enrollment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;


public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    @Query("SELECT new com.solo.lock.domain.enrollment.dto.response.PopularLectureRow(e.lecture.id, COUNT(e)) " +
            "FROM Enrollment e " +
            "GROUP BY e.lecture.id " +
            "ORDER BY COUNT(e) DESC")
    List<PopularLectureRow> findPopular(Pageable pageable);
}
