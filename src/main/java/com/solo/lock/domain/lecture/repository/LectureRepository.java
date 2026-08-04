package com.solo.lock.domain.lecture.repository;

import com.solo.lock.domain.lecture.entity.Lecture;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Lecture l SET l.enrolledCount = l.enrolledCount + 1 " +
            "WHERE l.id = :id AND l.enrolledCount < l.capacity")
    int updateIncrease(@Param("id") Long lectureId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lecture l where l.id = :id")
    Lecture findByIdForUpdate(@Param("id") Long lectureId);

    // [RED] fencing 없음 — 무조건 덮어씀 (뒤늦은 좀비 write도 그대로 반영됨)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Lecture l SET l.enrolledCount = :value WHERE l.id = :id")
    int writeNoFence(@Param("id") Long id, @Param("value") int value);

    // [GREEN] fencing — 저장된 토큰보다 큰 토큰만 허용 (낮은 토큰=좀비 write는 0 rows로 거부)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Lecture l SET l.enrolledCount = :value, l.fenceToken = :token " +
            "WHERE l.id = :id AND l.fenceToken < :token")
    int writeWithFence(@Param("id") Long id, @Param("value") int value, @Param("token") long token);
}
