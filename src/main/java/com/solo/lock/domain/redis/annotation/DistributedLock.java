package com.solo.lock.domain.redis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 분산 락을 선언적으로 걸기 위한 표식(marker) 어노테이션.
 * 실제 락 로직은 DistributedLockAspect가 담당한다.
 */
@Target(ElementType.METHOD)           // 메서드에만 붙일 수 있음
@Retention(RetentionPolicy.RUNTIME)   // 런타임에 리플렉션으로 읽어야 하므로 필수
public @interface DistributedLock {    // class가 아니라 @interface

    String key();                                 // 락 키 (SpEL 표현식, 예: "'lecture:' + #lectureId")

    long waitTime() default 5L;                   // 락 획득을 기다리는 최대 시간

    long leaseTime() default -1L;                 // -1 이면 watchdog ON (자동 연장)

    TimeUnit timeUnit() default TimeUnit.SECONDS; // 위 시간들의 단위
}
