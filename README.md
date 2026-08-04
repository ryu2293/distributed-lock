# 동시성 제어 & 분산 락 — 수강신청 오버셀 해결기

정원이 정해진 강의에 여러 명이 **동시에 신청**할 때 발생하는 **오버셀(정원 초과)** 과 **따닥(중복 신청)** 문제를,
단일 JVM 락 → DB 락 → 분산 락까지 **단계적으로 해결하며 각 방식을 실측 비교**한 학습/포트폴리오 프로젝트.

> 각 단계는 **RED(문제 재현) → GREEN(해결)** 흐름의 브랜치/PR로 구성되어 있습니다.

## 기술 스택
- Java 21, Spring Boot 3, Spring Data JPA
- MySQL 8 (다중 인스턴스 검증), H2 (단위 테스트)
- Redis 7 — Lettuce(직접 구현) / Redisson(RLock)
- k6 (부하 테스트), Docker Compose

---

## 문제 정의

| 문제 | 설명 | 원인 |
|------|------|------|
| **오버셀** | 정원 50인데 51명 이상 신청 성공 | `count++` 의 read-modify-write 경합 (lost update) |
| **따닥** | 같은 학생이 같은 강의를 여러 번 신청 | 중복 검사와 삽입 사이의 경합 |

두 문제는 **직교**한다 — 오버셀은 *수량* 문제(락으로 해결), 따닥은 *유일성* 문제(unique 제약으로 해결).

---

## 단계별 해결 과정

| 단계 | 브랜치 | 해결책 | 핵심 개념 |
|:---:|--------|--------|-----------|
| 1 | `step1/reproduce-race` | 문제 재현 | race condition, lost update |
| 2 | `step2/jvm-lock` | `synchronized` + Facade | `@Transactional`+lock 순서 함정 (락은 트랜잭션 바깥) |
| 3 | `step3/db-lock` | 원자적 UPDATE / 비관락 / 낙관락 + unique | DB 락, 영속성 컨텍스트, 재시도 |
| 4 | `step4/distributed-lock` | Redis 분산 락 (Lettuce→Redisson→AOP) | SETNX, Lua, watchdog, Pub/Sub |
| 5 | `step5/multi-instance-loadtest` | 다중 인스턴스 검증 + 부하테스트 | 분산 락 필요성 증명, 성능/한계 |

### 2단계 — `@Transactional` + `synchronized` 함정
메서드에 `synchronized`만 붙이면 **AOP 트랜잭션이 synchronized 바깥**을 감싸서 **커밋 전에 락이 풀린다** → 여전히 오버셀.
→ **Facade 패턴**: 락은 트랜잭션 *바깥*에서 감싸고, 트랜잭션은 별도 빈으로 분리.

### 4단계 — 분산 락 3단 진화
| 방식 | 클래스 | 특징 |
|------|--------|------|
| Lettuce SETNX (직접 구현) | `DistributedLockFacade` | `SET NX PX` + UUID 토큰 + Lua 안전해제 + **spin lock** |
| Redisson RLock | `RedissonFacade` | `tryLock`/`unlock`, **Pub/Sub 대기**, **watchdog**, 재진입 |
| Redisson + AOP | `AopLockFacade` + `@DistributedLock` + Aspect | 어노테이션 한 줄, 락 로직을 비즈니스에서 분리 (SpEL로 키 생성) |

---

## 5단계 — 다중 인스턴스 실측 (핵심)

앱 **2대(8080, 8081)** 가 **같은 MySQL + Redis** 를 공유하도록 띄우고, k6로 부하를 가해 검증.
단일 JVM 테스트로는 `synchronized` 도 통과하는 착시가 있어, **다중 인스턴스에서만 진실이 드러난다.**

### 정확성 — 정원 50, 동시 100, 2대 분산

| 락 | 결과 | 판정 |
|----|------|------|
| **none** (락 없음) | 행 37 / count 16, **데드락 다수** | ❌ 데이터 붕괴 |
| **sync** (JVM 락) | 행 **96~99** (오버셀), flaky | ❌ 인스턴스 간 무의미 |
| **pess / redisson / aop / lettuce** | 행 50 / count 50 | ✅ 정확 |

> `sync`는 "성공 응답"은 많지만 **enrolledCount가 실제의 절반**(lost update)일 만큼 붕괴 → JVM 락은 다중 인스턴스에서 조율 불가.

### 성능 — 정원=학생=3000, VU 200, waitTime 60s 통일 (거절 없는 순수 처리량)

| 락 | 처리량 | 중앙값(med) | p95 | 특징 |
|----|:-----:|:----------:|:---:|------|
| **pess** (비관락) | ~180/s | — | **1.93s** | DB 한 곳(홉 최소) → 최고 처리량·최저 꼬리 |
| **redisson** | ~157/s | ~820ms | ~3.3s | Pub/Sub → 공정, 꼬리 안정 |
| **aop** | ~169/s | — | ~4s | redisson 기반 + 프록시 오버헤드 |
| **lettuce** | ~136/s | **~460ms** | **~5.4s** | spin → 전형은 빠르나 불공정(꼬리 폭발) |
| **none** | ~480/s | — | — | ❌ 빠르지만 데이터 붕괴(데드락) |

**해석**
- **비관락이 처리량 1위** — DB-row 자원에선 락+데이터가 한 곳이라 홉이 적다.
- **lettuce(spin) vs redisson(pub/sub)**: lettuce는 **중앙값은 낮지만 p95(꼬리)가 폭발** — spin lock의 불공정(starvation) 때문. 실무 SLA는 꼬리로 잡으므로 redisson 우세.
- **avg가 아니라 median/p95(분포)** 를 봐야 진실이 보인다.

---

## 비관락 vs 분산 락 — 언제 무엇을?

| 항목 | 비관락 (DB) | Redis 분산 락 |
|------|------------|---------------|
| 속도(네트워크 홉) | 빠름 (DB 한 곳) | 한 홉 느림 |
| 대기 중 DB 커넥션 | **점유** (풀 고갈 위험) | 미점유 (락 획득 후에만 사용) |
| 고경합 시 | 커넥션 풀 마름 → **앱 전체 장애** 위험 | 대기는 Redis에서 → **DB 격리/보호** |
| DB로 표현 안 되는 자원 | 불가 | 가능 (외부 API 단일화, 캐시 갱신, 배치 단독 실행) |

→ **결론**: 순수 DB-row 자원 + 낮은 경합이면 DB 락이 간단하고 빠르다. **고경합·다중 인스턴스·DB 밖 자원** 이면 분산 락으로 DB를 보호하고 범용성을 얻는다.

---

## Redis 분산 락의 한계와 보완

- **RedLock**: 단일 Redis 장애/복제 지연 대비 — 독립 노드 N개 중 과반 획득 (Kleppmann의 반론 존재)
- **Fencing token**: GC 멈춤·failover로 "옛 홀더가 뒤늦게 쓰는" 문제를 단조 증가 토큰으로 차단 (근본 해결)
- **Zookeeper**: 순차 노드 기반, 강한 일관성(CP) vs Redis(AP)
- **watchdog 주의**: 프로세스가 살아있는데 작업이 무한 지연되면 watchdog가 계속 갱신 → **좀비 락**. 작업 타임아웃 + leaseTime 상한으로 방어.

---

## 실행 방법

```bash
# 1. 인프라 기동 (MySQL 3307, Redis 6379)
docker compose up -d

# 2. 단위/동시성 테스트 (H2)
./gradlew test

# 3. 다중 인스턴스 부하테스트
./gradlew bootJar
java -jar build/libs/lock-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql --server.port=8080 &
java -jar build/libs/lock-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql --server.port=8081 &

# lock: none | sync | pess | lettuce | redisson | aop
./loadtest/run-benchmark.sh redisson 3000 200
```

## 프로젝트 구조

```
src/main/java/com/solo/lock
├─ domain
│  ├─ lecture      # 강의 (정원 카운터, @Version 낙관락)
│  ├─ student
│  ├─ enrollment   # 신청 (service / facade / controller)
│  └─ redis        # 분산 락 (facade / aop / annotation / repository)
└─ config          # RedissonConfig
loadtest/          # k6 부하테스트 스크립트
```
