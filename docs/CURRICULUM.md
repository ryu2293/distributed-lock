git# 멀티스레드 동시성 & 분산 락 — 학습 커리큘럼

> 도메인: **수강신청** (정원이 있는 강의 = "재고" 모델)
> 목표: 동시성 문제를 **직접 재현**하고, 단일 JVM → DB → 분산 순서로 **해결책을 하나씩 체득**한다.
> 원칙: 코드는 직접 작성. AI에는 개념·이론만 질문. 각 단계는 **브랜치 1개 + 재현 테스트 1개**.

---

## 학습 서사 (한 줄 요약)

```
문제 재현 → 단일 JVM 락 → DB 락 → 분산 락 → 비교·정리
 (깨짐)     (한계 체감)   (실무 해법)  (여러 서버)  (선택 기준)
```

핵심 관통 질문: **"이 자원을 지키는 데, 지금 내 시스템 구조에서 가장 단순하면서 충분히 안전한 방법은 무엇인가?"**

---

## 0단계 — 프로젝트 세팅 ✅ (완료)

- **개념**: 도메인 모델링, JPA 엔티티 기본기
- **한 것**: Student / Lecture / Enrollment 엔티티, Repository, `application.yml`(H2·JPA·Redis), git + GitHub
- **엔티티 핵심**: `Lecture.enrolledCount`(정원 카운터)가 이번 프로젝트의 **동시성 타겟**
- **남은 보강** (다음 단계에서 자연스럽게 추가):
  - `Enrollment`에 `unique(student_id, lecture_id)` — 따닥(중복신청) 방지 → 3단계
  - `Lecture`에 `@Version` — 낙관락 → 3단계
- **스스로 답할 질문**
  - `ddl-auto`를 `create`로 둔 이유? 운영에서 왜 쓰면 안 되나?
  - `open-in-view: false`로 둔 이유는?

---

## 1단계 — 동시성 문제 "재현하기" 🔴

> 슬라이드 01 (멀티스레드 / Race Condition / Lost Update)

- **목표**: 정원을 **넘겨서** 신청되는 걸 테스트로 눈으로 확인. 이 단계는 **고치지 않는다.**
- **개념**
  - Process vs Thread — 무엇을 공유하나 (Heap 공유 = 문제의 출발점)
  - Race Condition, Lost Update — `count++`은 read→+1→write 3단계
- **만들 것**
  - 수강신청 서비스: `조회 → 정원 확인 → enrolledCount++ → Enrollment 저장`
  - 단일 요청 테스트(정상) 1개
  - **동시성 테스트**: 정원 N인 강의에 N+α명이 `ExecutorService`로 동시 신청
    - 힌트: `ExecutorService` + `CountDownLatch`로 동시 출발, `future.get()`으로 종료 대기
- **스스로 답할 질문**
  - 왜 `enrolledCount`가 정원보다 커지나? 어느 줄에서 두 스레드가 같은 값을 읽나?
  - 정원 체크를 `count(Enrollment)` 쿼리로 바꾸면 안전해질까? (→ 아니다. 왜?)
- **완료 기준**: "정원 50인데 신청 성공 55건" 같은 **오버셀을 테스트가 재현**하고, 그게 실패(RED)로 남는다.

---

## 2단계 — 단일 JVM에서의 해결 🟡

> 슬라이드 02 (synchronized · Lock · volatile · Atomic/CAS · ConcurrentHashMap · 불변)

- **목표**: 서버 1대 안에서 락으로 막아보고, **그 한계까지** 체감한다.
- **개념**
  - `synchronized` / `ReentrantLock`(tryLock·타임아웃·공정성)
  - `volatile`은 가시성만, 원자성은 아님 / `AtomicInteger`(CAS)
  - 락 vs 원자연산(낙관적)의 갈림
- **만들 것**
  - 1단계 서비스에 `synchronized`(또는 Lock) 적용 → 오버셀 사라지는지 테스트로 확인
- **스스로 답할 질문** ⭐ (가장 중요)
  - 메서드에 `@Transactional` + `synchronized`를 걸었는데도 왜 오버셀이 날 수 있나?
    (힌트: **락 해제 시점 vs 트랜잭션 커밋 시점**의 순서)
  - `synchronized`가 **서버 2대**에선 왜 무력한가?
- **완료 기준**: 단일 JVM 테스트는 GREEN. 하지만 "한계 2가지"를 문서(README)에 적을 수 있다.

---

## 3단계 — DB 차원의 락 🟢

> 슬라이드 04(비관/낙관) + 11~11c(unique index) + 20(원자적 UPDATE) + isolation

- **목표**: 실무에서 가장 많이 쓰는 **DB 기반 해법 3~4가지**를 구현·비교한다.
- **개념 & 만들 것** (각각 브랜치 추천)
  1. **원자적 UPDATE** — `UPDATE lecture SET enrolled_count=enrolled_count+1 WHERE id=? AND enrolled_count < capacity`, `영향받은 행 == 1`일 때만 성공 처리
  2. **비관락** — `@Lock(PESSIMISTIC_WRITE)` + `findByIdForUpdate` (SELECT ... FOR UPDATE)
  3. **낙관락** — `Lecture`에 `@Version` 추가, `OptimisticLockException` → **재시도 로직**
  4. **unique 제약** — `Enrollment(student_id, lecture_id)` 유니크로 따닥 방지
- **스스로 답할 질문**
  - 셋 중 경합이 **심할 때**/ **드물 때** 각각 뭐가 유리한가?
  - unique index로 **막는 것**과 **못 막는 것**은? (따닥 vs 오버셀)
  - "비관락 = DB가 주는 분산 락"이라는 말의 의미와, 그래도 분산 락이 필요한 경우는?
  - isolation level(READ COMMITTED / REPEATABLE READ)만 올리면 오버셀이 막히나? (→ 아니다. 왜?)
- **완료 기준**: 3가지 방식 모두 오버셀 방지 GREEN + 각 방식의 트레이드오프 표 작성.

---

## 4단계 — 분산 락 (Redis) 🔵

> 슬라이드 03(SETNX·싱글스레드·안전 해제) + 05(Lettuce vs Redisson·재진입·watchdog)

- **목표**: 여러 서버가 공유하는 **외부 락**을 구현한다. 직접 → 라이브러리 순.
- **사전 준비**: 로컬 Redis 실행 (`docker run -p 6379:6379 redis` 등)
- **개념 & 만들 것**
  1. **Lettuce + SETNX 직접 구현** — `SET key val NX PX 3000`(획득+만료 원자), 해제는 **토큰(UUID) 일치 시에만 Lua로 삭제**
  2. **Redisson `RLock`으로 리팩터링** — `tryLock/unlock`, 재진입, watchdog 자동 연장
- **스스로 답할 질문**
  - SETNX 후 따로 EXPIRE 걸면 뭐가 위험한가? (데드락)
  - "내 락인 줄 알고 남의 락을 지우는" 사고는 왜 나고, 토큰+Lua가 어떻게 막나?
  - Redis가 **싱글 스레드**인 게 분산 락에 왜 유리한가?
  - Redisson **watchdog**은 무엇을 자동으로 해주나? `leaseTime`을 직접 주면 왜 watchdog가 꺼지나?
  - **재진입**이 없으면 어떤 데드락이 나나?
- **완료 기준**: (선택) 앱을 2개 포트로 띄워 동시 신청해도 오버셀 없음 + 안전 해제 구현.

---

## 5단계 — 심화 · 비교 · 정리 📚

> 슬라이드 05(멀티노드·RedLock·Zookeeper) + 06(요약·교훈)

- **목표**: "무엇을 언제 쓰나"를 **판단 기준**으로 정리 → 포트폴리오 완성.
- **개념 (구현보다 이해 중심)**
  - 멀티 노드 Redis의 빈틈(failover 시 락 중복) → RedLock, 그리고 그 **논쟁**(Kleppmann)
  - **펜싱 토큰(fencing token)** — 최종 정합성 보증
  - Zookeeper: 세션 기반 해제(TTL 추측 불필요), CP, 순차 노드
  - "스타트업 현실은 단순하다" — Redis 1대 / DB writer 1대면 단일 노드 락·DB 락으로 충분
- **만들 것 (포폴 자산)**
  - **벤치마크 표**: 방식별 처리량(TPS)·정확성·복잡도 비교 (동일 부하 테스트)
  - **README**: 문제 재현 → 각 해법 → 선택 기준. 커밋 히스토리로 서사 증명
- **스스로 답할 질문**
  - "정확성이 절대 중요한 작업(돈·재고)"에 분산 락만 믿으면 왜 부족한가?
  - 내 프로젝트 규모라면 최종적으로 뭘 고르겠나? 그 근거는?

---

## 진행 체크리스트

- [x] 0단계 — 세팅 & GitHub
- [ ] 1단계 — 동시성 문제 재현 (RED 테스트)
- [ ] 2단계 — 단일 JVM 락 + 한계 정리
- [ ] 3단계 — DB 락 3종 + 비교표
- [ ] 4단계 — Redis 분산 락 (Lettuce → Redisson)
- [ ] 5단계 — 벤치마크 + README 서사

## 브랜치 전략 (권장)

```
main                      # 안정 상태
 └ step1/reproduce-race   # 각 단계별 브랜치 → PR → main 병합
 └ step2/jvm-lock
 └ step3/db-lock
 └ step4/distributed-lock
```

## 학습 태도

- 각 단계에서 **테스트로 먼저 깨뜨리고(RED), 고쳐서 통과(GREEN)** — before/after가 곧 포트폴리오 서사.
- 해결책을 넣기 전에 항상 먼저 묻기: **"정말 이 락이 필요한 자리인가? 더 단순한 방법은?"**
