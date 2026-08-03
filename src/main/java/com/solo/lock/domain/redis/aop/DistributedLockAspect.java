package com.solo.lock.domain.redis.aop;

import com.solo.lock.domain.redis.annotation.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @DistributedLock 이 붙은 메서드를 가로채서, 실행 전후로 Redisson 분산 락을 걸고 푼다.
 * 락(횡단 관심사)과 비즈니스 로직을 분리하는 것이 목적.
 */
@Aspect        // "나는 부가기능(횡단관심사)을 담당하는 클래스다"
@Component     // 스프링 빈으로 등록돼야 AOP 프록시가 만들어짐
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;

    // SpEL 파서 & 파라미터 이름 탐색기 — 상태가 없어 재사용 가능
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    // @DistributedLock 이 달린 메서드를 통째로 감싼다(around)
    @Around("@annotation(com.solo.lock.domain.redis.annotation.DistributedLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        // ── 1. 가로챈 메서드에서 @DistributedLock 정보 꺼내기 ──
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock ann = method.getAnnotation(DistributedLock.class);

        // ── 2. SpEL로 키 계산: "'lecture:' + #lectureId" → "lecture:42" ──
        String key = parseKey(ann.key(), method, joinPoint.getArgs());

        // ── 3. Redisson 락 획득 ──
        RLock lock = redissonClient.getLock(key);
        boolean acquired = lock.tryLock(ann.waitTime(), ann.leaseTime(), ann.timeUnit());
        if (!acquired) {
            throw new RuntimeException("락 획득 실패: " + key);
        }

        // ── 4. 원래 메서드 실행 후 반드시 해제 ──
        try {
            return joinPoint.proceed();                        // ← 진짜 비즈니스 메서드 호출
        } finally {
            if (lock.isHeldByCurrentThread()) {                // 내가 들고 있을 때만 해제
                lock.unlock();
            }
        }
    }

    // SpEL 표현식을 실제 파라미터 값으로 평가해 최종 키 문자열을 만든다
    private String parseKey(String spel, Method method, Object[] args) {
        // #파라미터이름 → 실제 인자 값 매핑을 담아주는 컨텍스트
        MethodBasedEvaluationContext context =
                new MethodBasedEvaluationContext(null, method, args, nameDiscoverer);
        return parser.parseExpression(spel).getValue(context, String.class);
    }
}
