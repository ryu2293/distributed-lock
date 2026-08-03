package com.solo.lock.domain.enrollment.service;

import com.solo.lock.domain.enrollment.facade.EnrollmentFacade;
import com.solo.lock.domain.enrollment.repository.EnrollmentRepository;
import com.solo.lock.domain.lecture.entity.Lecture;
import com.solo.lock.domain.lecture.repository.LectureRepository;
import com.solo.lock.domain.redis.facade.DistributedLockFacade;
import com.solo.lock.domain.student.entity.Student;
import com.solo.lock.domain.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EnrollmentConcurrencyTest {

    @Autowired EnrollmentService enrollmentService;
    @Autowired LectureRepository lectureRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired EnrollmentFacade enrollmentFacade;
    @Autowired DistributedLockFacade distributedLockFacade;

    @Test
    void 동시에_100명이_정원50강의를_신청하면_오버셀이_난다() throws InterruptedException {
        // ── given : 데이터 준비 ──
        int capacity = 50;
        int studentCount = 100;

        Lecture lecture = lectureRepository.save(
                Lecture.builder().title("동시성").capacity(capacity).enrolledCount(0).build());
        Long lectureId = lecture.getId();

        List<Long> studentIds = new ArrayList<>();
        for (int i = 0; i < studentCount; i++) {
            Student student = studentRepository.save(
                    Student.builder().schoolId("s" + i).name("학생" + i).build());
            studentIds.add(student.getId());
        }

        // ── when : 100명이 동시에 신청 ──
        ExecutorService pool = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(studentCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (Long studentId : studentIds) {
            pool.submit(() -> {
                try {
                    distributedLockFacade.enroll(studentId, lectureId);
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();       // 100개 스레드가 다 끝날 때까지 대기
        pool.shutdown();

        // ── then : 결과 검증 ──
        Lecture result = lectureRepository.findById(lectureId).orElseThrow();
        long enrollmentRows = enrollmentRepository.count();

        System.out.println("성공=" + success.get() + " 실패=" + fail.get());
        System.out.println("enrolledCount=" + result.getEnrolledCount());
        System.out.println("Enrollment 행 수=" + enrollmentRows);

        assertThat(enrollmentRows).isEqualTo(capacity);
    }

    @Test
    void 같은_학생이_같은_강의를_동시에_여러번_신청하면_중복된다() throws InterruptedException {
        // ── given : 학생 1명, 강의 1개 (정원 넉넉히) ──
        Lecture lecture = lectureRepository.save(
                Lecture.builder().title("따닥").capacity(100).enrolledCount(0).build());
        Long lectureId = lecture.getId();

        Student student = studentRepository.save(
                Student.builder().schoolId("s1").name("학생1").build());
        Long studentId = student.getId();          // ★ 딱 한 명. 이 id를 반복 사용

        int tryCount = 10;                          // 같은 신청을 10번 따닥

        // ── when : 같은 (studentId, lectureId)를 동시에 10번 ──
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(tryCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int i = 0; i < tryCount; i++) {
            pool.submit(() -> {
                try {
                    enrollmentFacade.optimisticEnrollFacade(studentId, lectureId);   // 같은 학생 반복
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();

        // ── then ──
        long rows = enrollmentRepository.count();
        System.out.println("성공=" + success.get() + " 실패=" + fail.get() + " 행 수=" + rows);

        assertThat(rows).isEqualTo(1);   // 한 학생은 한 번만 → 지금은 RED(여러 건)
    }
}