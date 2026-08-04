package com.solo.lock.domain.enrollment.controller;

import com.solo.lock.domain.enrollment.facade.EnrollmentFacade;
import com.solo.lock.domain.enrollment.repository.EnrollmentRepository;
import com.solo.lock.domain.enrollment.service.EnrollmentService;
import com.solo.lock.domain.lecture.entity.Lecture;
import com.solo.lock.domain.lecture.repository.LectureRepository;
import com.solo.lock.domain.redis.facade.AopLockFacade;
import com.solo.lock.domain.redis.facade.DistributedLockFacade;
import com.solo.lock.domain.redis.facade.RedissonFacade;
import com.solo.lock.domain.student.entity.Student;
import com.solo.lock.domain.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final EnrollmentFacade enrollmentFacade;
    private final RedissonFacade redissonFacade;
    private final DistributedLockFacade distributedLockFacade;
    private final AopLockFacade aopLockFacade;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final LectureRepository lectureRepository;


    @PostMapping("/enrollments")
    public ResponseEntity<String> enroll(
            @RequestParam Long studentId,
            @RequestParam Long lectureId,
            @RequestParam String lock
    ) throws InterruptedException {
        switch (lock) {
            case "none" -> enrollmentService.enroll(studentId, lectureId);
            case "sync" -> enrollmentFacade.enrollFacade(studentId, lectureId);
            case "pess" -> enrollmentService.pessimisticEnroll(studentId, lectureId);
            case "lettuce" -> distributedLockFacade.enroll(studentId, lectureId);
            case "redisson" -> redissonFacade.enroll(studentId, lectureId);
            case "aop" -> aopLockFacade.enroll(studentId, lectureId);
            default -> throw new IllegalArgumentException("Unknown lock: " + lock);
        }

        return ResponseEntity.ok("OK");
    }

    @PostMapping("/setup")
    public ResponseEntity<Map<String, Object>> setup(
            @RequestParam int capacity,
            @RequestParam int studentCount
    ) {
        enrollmentRepository.deleteAll();
        studentRepository.deleteAll();
        lectureRepository.deleteAll();

        Lecture lecture = lectureRepository.save(
                Lecture.builder().title("Test").capacity(capacity).enrolledCount(0).build());

        List<Long> studentIds = new ArrayList<>();
        for (int i = 0; i < studentCount; i++) {
            Student student = studentRepository.save(
                    Student.builder().schoolId("s" + i).name("학생" + i).build());
            studentIds.add(student.getId());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of("lectureId", lecture.getId(), "studentIds", studentIds));
    }

    @GetMapping("lectures/{id}/result")
    public ResponseEntity<Map<String, Object>> result(@PathVariable Long id) {
        Lecture lecture = lectureRepository.findById(id).orElseThrow();
        long enrollmentCount = enrollmentRepository.count();

        return ResponseEntity.ok(Map.of(
                "capacity", lecture.getCapacity(),
                "enrolledCount", lecture.getEnrolledCount(),
                "enrollmentRows", enrollmentCount
        ));
    }
}
