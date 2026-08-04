package com.solo.lock.domain.enrollment.service;

import com.solo.lock.domain.enrollment.entity.Enrollment;
import com.solo.lock.domain.enrollment.repository.EnrollmentRepository;
import com.solo.lock.domain.lecture.entity.Lecture;
import com.solo.lock.domain.lecture.repository.LectureRepository;
import com.solo.lock.domain.student.entity.Student;
import com.solo.lock.domain.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CacheStampedeTest {

    @Autowired PopularLectureService popularLectureService;
    @Autowired LectureRepository lectureRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired EnrollmentRepository enrollmentRepository;

    @Test
    void 캐시_만료_동시요청시_재계산이_여러번_발생한다() throws InterruptedException {
        // ── given : 집계할 데이터 시드 + 캐시 비우기 ──
        seed();
        popularLectureService.resetCache();

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        // ── when : 빈 캐시에 동시 조회 (미스 몰림) ──
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    popularLectureService.getPopular();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();

        // ── then ──
        int recompute = popularLectureService.getRecomputeCount();
        System.out.println("재계산 횟수 = " + recompute);
        assertThat(recompute).isEqualTo(1);   // ← RED: 락이 없어 실제론 여러 번 재계산됨
    }

    private void seed() {
        enrollmentRepository.deleteAll();
        studentRepository.deleteAll();
        lectureRepository.deleteAll();
        for (int l = 0; l < 3; l++) {
            Lecture lecture = lectureRepository.save(
                    Lecture.builder().title("L" + l).capacity(100).enrolledCount(0).build());
            for (int s = 0; s <= l; s++) {   // 강의마다 신청 수 다르게
                Student student = studentRepository.save(
                        Student.builder().schoolId("s" + l + "_" + s).name("n").build());
                enrollmentRepository.save(
                        Enrollment.builder().student(student).lecture(lecture).build());
            }
        }
    }
}
