package com.solo.lock.domain.redis;

import com.solo.lock.domain.lecture.entity.Lecture;
import com.solo.lock.domain.lecture.repository.LectureRepository;
import com.solo.lock.domain.redis.facade.FencingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시나리오(좀비 라이터):
 *  A: 락 획득(lease 2s), 토큰 발급, 3초 멈춤(→ 그 사이 락 만료) 후 value=50 쓰기
 *  B: A의 락이 만료된 뒤 획득, 토큰 발급(A보다 큼), 멈춤 없이 value=100 쓰기
 *  타임라인: t0 A락(token1) … t2 A락만료 … t2.5 B락(token2)·B가 100 씀 … t3 A가 뒤늦게 50 씀
 */
@SpringBootTest
class FencingTokenTest {

    @Autowired FencingService fencingService;
    @Autowired LectureRepository lectureRepository;
    @Autowired StringRedisTemplate redisTemplate;

    @Test
    void RED_fencing없으면_좀비_A가_최신값_B를_덮어쓴다() throws Exception {
        Long id = seed();
        int[] rows = run(id, false);   // useFence = false

        int finalCount = lectureRepository.findById(id).orElseThrow().getEnrolledCount();
        System.out.println("[RED] A쓰기=" + rows[0] + " B쓰기=" + rows[1] + " 최종count=" + finalCount);

        // A(50)가 B(100)보다 늦게 무조건 덮어써서 최종이 50으로 오염됨
        assertThat(finalCount).isEqualTo(50);
    }

    @Test
    void GREEN_fencing있으면_좀비_A의_쓰기가_거부된다() throws Exception {
        Long id = seed();
        int[] rows = run(id, true);    // useFence = true

        int finalCount = lectureRepository.findById(id).orElseThrow().getEnrolledCount();
        System.out.println("[GREEN] A쓰기=" + rows[0] + " B쓰기=" + rows[1] + " 최종count=" + finalCount);

        assertThat(rows[1]).isEqualTo(1);        // B(큰 토큰)는 성공
        assertThat(rows[0]).isEqualTo(0);        // A(작은 토큰=좀비)는 거부
        assertThat(finalCount).isEqualTo(100);   // 최종은 B값 유지
    }

    /** A, B 두 스레드를 타이밍 맞춰 실행하고 각자의 쓰기 row수를 반환 */
    private int[] run(Long id, boolean useFence) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // A: 먼저 락(lease 2s), 3초 멈춤 후 value=50 쓰기
        Future<Integer> fa = pool.submit(() -> fencingService.doWork(id, 50, useFence, 3000, 2));
        // B: A의 lease(2s) 만료를 기다린 뒤 락, 멈춤 없이 value=100 쓰기
        Future<Integer> fb = pool.submit(() -> {
            Thread.sleep(2500);
            return fencingService.doWork(id, 100, useFence, 0, 5);
        });

        int ra = fa.get(20, TimeUnit.SECONDS);
        int rb = fb.get(20, TimeUnit.SECONDS);
        pool.shutdown();
        return new int[]{ra, rb};
    }

    private Long seed() {
        lectureRepository.deleteAll();
        Lecture lecture = lectureRepository.save(
                Lecture.builder().title("fence").capacity(1000).enrolledCount(0).build());
        redisTemplate.delete("fence:lecture:" + lecture.getId());   // 토큰 카운터 리셋 → A=1, B=2
        return lecture.getId();
    }
}
