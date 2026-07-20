package com.solo.lock.domain.lecture.repository;

import com.solo.lock.domain.lecture.entity.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Lecture l SET l.enrolledCount = l.enrolledCount + 1 " +
            "WHERE l.id = :id AND l.enrolledCount < l.capacity")
    int updateIncrease(@Param("id") Long lectureId);
}
