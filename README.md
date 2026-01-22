# Spring에서 비동기(Async) 처리 방식 비교: @Async / BlockingQueue

# Overview

해당 문서에서는 자바 + Spring에서 멀티스레드 작업을 처리하는 방식은 `@Async + Executor` 방식과 `BlockingQueue + Worker` 방식을 비교합니다.

각 방식의 서블릿 기반 Web 애플리케이션(Spring)에서 블로킹과 논블로킹 방식을 비교합니다.

100개의 엔티티를 다수의 비동기 스레드로 DB에 적재하는 시나리오를 바탕으로 비교합니다.

전체 소스 코드는 [여기](https://github.com/gyehyun-bak/spring-async-study)에서 확인 가능합니다.

---

# @Async + Executor

`@Async` 어노테이션을 통해 지정된 `Executor`에서 메서드를 **별도 스레드로 비동기 실행**한다. 호출자는 작업 완료를 기다리지 않고 즉시 다음 로직을 수행한다. 이는 기본 거부 정책(AbortPolicy) 기준이며, 설정한 거부 정책에 따라 동작이 달라질 수 있다.

- `AbortPolicy` (Default): 큐가 가득 차면 `RejectedExecutionException`을 발생시키고 작업을 거부
- `CallerRunsPolicy`: 큐가 가득 차면 호출자 스레드에서 작업을 직접 실행하여 결과적으로 호출자를 블로킹
- `DiscardPolicy`: 큐가 가득 차면 예외 없이 해당 작업을 조용히 폐기

## AsyncService

각 정책(Policy)별 메소드를 구현.

- `@Async` 메서드는 **프록시 기반**으로 동작하므로, 반드시 **외부 빈에서 호출**되어야 한다
- 각 호출은 Executor의 스레드 풀 정책에 따라 **즉시 실행 또는 큐 대기**된다

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncService {

    private final AsyncTaskLogRepository repository;

    /**
     * AbortPolicy (기본)
     * - 큐가 가득 차면 RejectedExecutionException 발생
     */
    @Async("defaultAbortExecutor")
    @Transactional
    public void processDefault(int taskId) {
        log.info("[ABORT] start {}", taskId);
        sleep(2000);
        repository.save(new AsyncTaskLog("ASYNC-ABORT", taskId));
        log.info("[ABORT] end {}", taskId);
    }

    /**
     * CallerRunsPolicy
     * - 큐가 가득 차면 호출 스레드가 직접 실행
     */
    @Async("callerRunsExecutor")
    @Transactional
    public void processCallerRuns(int taskId) {
        log.info("[CALLER-RUNS] start {}", taskId);
        sleep(2000);
        repository.save(new AsyncTaskLog("ASYNC-CALLER-RUNS", taskId));
        log.info("[CALLER-RUNS] end {}", taskId);
    }

    /**
     * DiscardPolicy
     * - 큐가 가득 차면 태스크를 조용히 버림
     */
    @Async("discardExecutor")
    @Transactional
    public void processDiscard(int taskId) {
        log.info("[DISCARD] start {}", taskId);
        sleep(2000);
        repository.save(new AsyncTaskLog("ASYNC-DISCARD", taskId));
        log.info("[DISCARD] end {}", taskId);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

## Executor 빈 등록

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * AbortPolicy (기본)
     * - 큐가 가득 차면 RejectedExecutionException 발생
     * 별도로 .setRejectedExecutionHandler() 를 설정해주지 않으면 자동 AbortPolicy 사용
     */
    @Bean("defaultAbortExecutor")
    public Executor defaultAbortExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("ABORT-");
        executor.initialize();
        return executor;
    }

    /**
     * CallerRunsPolicy
     * - 큐가 가득 차면 호출 스레드가 직접 실행 (블로킹)
     */
    @Bean("callerRunsExecutor")
    public Executor callerRunsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("CALLER-RUNS-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * DiscardPolicy
     * - 큐가 가득 차면 태스크를 조용히 버림
     */
    @Bean("discardExecutor")
    public Executor discardExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("DISCARD-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
```

## Async 요청 컨트롤러

```java
@RestController
@RequiredArgsConstructor
public class AsyncController {

    private final AsyncService asyncService;

    /**
     * AbortPolicy (기본)
     * - 큐가 가득 차면 RejectedExecutionException 발생
     * - 즉시 응답, 예외로 실패 감지
     */
    @GetMapping("/async-default")
    public String asyncDefault() {
        long start = System.currentTimeMillis();

        int success = 0;
        int fail = 0;

        for (int i = 0; i < 100; i++) {
            try {
                asyncService.processDefault(i);
                success++;
            } catch (Exception e) {
                fail++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[AbortPolicy] 성공: %d, 실패: %d, 소요시간: %dms", success, fail, elapsed);
    }

    /**
     * CallerRunsPolicy
     * - 큐가 가득 차면 요청 스레드가 직접 태스크 실행 (블로킹)
     */
    @GetMapping("/async-caller-runs")
    public String asyncCallerRuns() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            asyncService.processCallerRuns(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[CallerRunsPolicy] 완료, 소요시간: %dms", elapsed);
    }

    /**
     * DiscardPolicy
     * - 큐가 가득 차면 태스크를 조용히 버림
     * - 즉시 응답, 실패 감지 불가
     */
    @GetMapping("/async-discard")
    public String asyncDiscard() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            asyncService.processDiscard(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[DiscardPolicy] 완료, 소요시간: %dms (일부 유실 가능)", elapsed);
    }
}
```

## 실행 결과

### `AbortPolicy` (기본) : 예외 발생

![호출 결과](image.png)

호출 결과

![저장 결과](image%201.png)

저장 결과

### `CallerRunsPolicy` : 블로킹

![호출 결과](image%202.png)

호출 결과

![저장 결과](image%203.png)

저장 결과

- 큐에서 넘친 일부 테스크를 요청 스레드가 처리

### `DiscardPolicy` : 논블로킹, 실패 테스크는 버림

![호출 결과](image%204.png)

호출 결과

![저장 결과](image%205.png)

저장 결과

- 예외를 던지지 않기 때문에 실패 개수 예측 어려움

---

# BlockingQueue + Worker

`BlockingQueue` 에 작업을 적재하고, 고정된 Worker 스레드가 **큐에서 하나씩 꺼내 순차적으로 처리**하는 방식.

1. `BlockingQueue.put()` : 큐가 가득 차면, 공간이 생길 때까지 호출 스레드를 block 하여 작업 유실 없이 적재
2. `BlockingQueue.offer()` : 큐가 가득 차면 즉시 false 를 반환하여 호출 스레드를 block 하지 않음 (유실 가능)

## QueueWorker

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueWorker {

    private final AsyncTaskLogRepository repository;

    private final BlockingQueue<QueueTask> queue = new LinkedBlockingQueue<>(10);

    @PostConstruct
    public void init() {
        for (int i = 0; i < 10; i++) {
            Thread worker = new Thread(this::consume);
            worker.setName("QUEUE-WORKER-" + i);
            worker.start();
        }
    }

    /**
     * 블로킹 방식: 큐가 가득 차면 대기
     */
    public void submitWithPut(QueueTask task) {
        try {
            queue.put(task);
            log.info("[QUEUE-PUT] submit {}", task.taskId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 논블로킹 방식: 큐가 가득 차면 즉시 false 반환
     */
    public boolean submitWithOffer(QueueTask task) {
        boolean added = queue.offer(task);
        if (added) {
            log.info("[QUEUE-OFFER] submit 성공 {}", task.taskId());
        } else {
            log.warn("[QUEUE-OFFER] submit 실패 (큐 가득 참) {}", task.taskId());
        }
        return added;
    }

    public void consume() {
        while (true) {
            try {
                QueueTask task = queue.take();
                process(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Transactional
    public void process(QueueTask task) throws InterruptedException {
        log.info("[{}] QUEUE start {}", Thread.currentThread().getName(), task.taskId());

        Thread.sleep(2000);

        repository.save(new AsyncTaskLog("QUEUE", task.taskId()));

        log.info("[{}] QUEUE end {}", Thread.currentThread().getName(), task.taskId());
    }
}
```

- Worker 스레드 수: **고정 10개**

## Queue 요청 컨트롤러

```java
@RestController
@RequiredArgsConstructor
public class QueueController {

    private final QueueWorker queueWorker;

    /**
     * 블로킹 방식 (put)
     * - 큐가 가득 차면 요청 스레드가 대기
     * - 모든 태스크가 큐에 들어갈 때까지 응답하지 않음
     */
    @GetMapping("/queue-put")
    public String queuePut() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            queueWorker.submitWithPut(new QueueTask(i));
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("PUT 방식 완료 - 소요시간: %dms (블로킹 발생)", elapsed);
    }

    /**
     * 논블로킹 방식 (offer)
     * - 큐가 가득 차면 즉시 실패 반환
     * - 요청 스레드가 대기하지 않음
     */
    @GetMapping("/queue-offer")
    public String queueOffer() {
        long start = System.currentTimeMillis();

        int success = 0;
        int fail = 0;

        for (int i = 0; i < 100; i++) {
            if (queueWorker.submitWithOffer(new QueueTask(i))) {
                success++;
            } else {
                fail++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("OFFER 방식 완료 - 성공: %d, 실패: %d, 소요시간: %dms (논블로킹)",
                success, fail, elapsed);
    }
}
```

- 동일하게 100개 작업 제출
- `/queue-put` : 큐가 가득 차면 **컨트롤러 스레드가 대기**
- `/queue-offer` : 큐가 가득 차면 즉시 실패 반환

## 실행 결과

### `.put()` : 블로킹

![호출 결과](image%206.png)

호출 결과

![저장 결과](image%207.png)

저장 결과

- 약 **20초 후 요청 완료**
- 모든 작업이 **순서대로 소비됨**
- 다중 스레드는 비동기로 처리하지만 스레드는 블락킹 방식

### `.offer()` : 논블로킹

![호출 결과](image%208.png)

호출 결과

![저장 결과](image%209.png)

저장 결과

- 요청 즉시 실패 반환(큐가 가득 참으로 인해)
- 일부(20개)만 저장

## `BlockingQueue`의 한계점

- BlockingQueue는 **단일 JVM 내에서는 매우 안정적**이나, 다중 인스턴스 환경에서는 확장성이 제한됨
    - Redis Queue / Redis Stream 혹은 Kafka 등 외부 메시지 브로커를 통해 보완 가능

---

# @Async와 BlockingQueue 비교

| 구분 | @Async + Executor | BlockingQueue + Worker |
| --- | --- | --- |
| 처리 방식 | Executor에 작업 제출 후 정책에 따라 처리 | 큐에 적재 후 Worker가 소비 |
| 처리량 초과 시 | 정책에 따라 예외 / 호출자 실행 / 폐기 | `put()`은 block, `offer()`는 실패 반환 |
| 블로킹 여부 | **정책에 따라 다름** (CallerRuns 시 블로킹) | `put()`은 블로킹, `offer()`는 논블로킹 |
| 작업 유실 가능성 | **있음** (Abort, Discard) | `put()`은 없음, `offer()`는 있음 |
| 호출자 제어 | 기본 비동기, 정책에 따라 동기화 가능 | 큐 API 선택으로 제어 가능 |
| 처리 순서 보장 | 보장 안 됨 | FIFO 보장 |
| 구현 난이도 | 낮음 | 상대적으로 높음 |
| 운영 안정성 | 정책 미설정 시 위험 | 동작이 명확하고 예측 가능 |
| 적합한 용도 | 정책 기반 비동기 작업 처리 | 정합성 중요한 Producer–Consumer 처리 |

### 결론

> `@Async` 역시 내부적으로는 `ThreadPoolExecutor` 기반의 **BlockingQueue + Worker 모델 위에서 동작한다.** 차이는 **거부 정책을 통해 동작을 선택하느냐**, **큐와 워커를 직접 제어하느냐** 의 차이다.
>

---

# (추가) 작업 유실이 없는 완전 논블로킹

앞선 방식들은 **논블로킹이면 작업 유실**, **작업 유실이 없으면 블로킹**이라는 트레이드오프가 있었다. 이 섹션에서는 **논블로킹이면서 작업 유실이 없는** 완전 비동기 방식들을 비교한다.

| 방식 | 논블로킹 | 작업 유실 없음 | 비고 |
| --- | --- | --- | --- |
| `@Async` + 무제한 큐 | O | O | 메모리 주의 |
| `CompletableFuture` | O | O | 체이닝 가능 |
| Virtual Thread (Java 21+) | O | O | 경량 스레드 |
| `@Async` + Virtual Thread | O | O | 스프링 통합 |

## Executor 빈 등록

```java
@Configuration
public class NonBlockingConfig {

    /**
     * 무제한 큐 Executor
     * - queueCapacity를 매우 크게 설정하여 거부 없이 모든 작업 수용
     * - 메모리 주의 필요
     */
    @Bean("unboundedQueueExecutor")
    public Executor unboundedQueueExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(Integer.MAX_VALUE); // 무제한 큐
        executor.setThreadNamePrefix("UNBOUNDED-");
        executor.initialize();
        return executor;
    }

    /**
     * Virtual Thread Executor (Java 21+)
     * - 경량 스레드로 대량의 동시 작업 처리
     */
    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Fixed Thread Pool
     * - CompletableFuture용 고정 스레드 풀
     */
    @Bean("fixedThreadPoolExecutor")
    public Executor fixedThreadPoolExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}
```

## NonBlockingService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class NonBlockingService {

    private final AsyncTaskLogRepository repository;
    private final Executor fixedThreadPoolExecutor;
    private final Executor virtualThreadExecutor;

    /**
     * 방식 1: @Async + 무제한 큐
     * - 스프링 @Async 사용
     * - 큐가 무제한이므로 거부 없음
     */
    @Async("unboundedQueueExecutor")
    @Transactional
    public void processWithUnboundedQueue(int taskId) {
        log.info("[UNBOUNDED-QUEUE] start {}", taskId);
        sleep(2000);
        repository.save(new AsyncTaskLog("UNBOUNDED-QUEUE", taskId));
        log.info("[UNBOUNDED-QUEUE] end {}", taskId);
    }

    /**
     * 방식 2: CompletableFuture + Fixed Thread Pool
     * - 직접 CompletableFuture로 비동기 실행
     * - 즉시 반환
     */
    public CompletableFuture<Void> processWithCompletableFuture(int taskId) {
        return CompletableFuture.runAsync(() -> {
            log.info("[COMPLETABLE-FUTURE] start {}", taskId);
            sleep(2000);
            saveLog("COMPLETABLE-FUTURE", taskId);
            log.info("[COMPLETABLE-FUTURE] end {}", taskId);
        }, fixedThreadPoolExecutor);
    }

    /**
     * 방식 3: Virtual Thread (Java 21+)
     * - 경량 가상 스레드 사용
     * - 대량의 동시 작업에 적합
     */
    public CompletableFuture<Void> processWithVirtualThread(int taskId) {
        return CompletableFuture.runAsync(() -> {
            log.info("[VIRTUAL-THREAD] start {}", taskId);
            sleep(2000);
            saveLog("VIRTUAL-THREAD", taskId);
            log.info("[VIRTUAL-THREAD] end {}", taskId);
        }, virtualThreadExecutor);
    }

    /**
     * 방식 4: @Async + Virtual Thread
     * - 스프링 @Async와 Virtual Thread 조합
     */
    @Async("virtualThreadExecutor")
    @Transactional
    public void processAsyncWithVirtualThread(int taskId) {
        log.info("[ASYNC-VIRTUAL] start {}", taskId);
        sleep(2000);
        repository.save(new AsyncTaskLog("ASYNC-VIRTUAL", taskId));
        log.info("[ASYNC-VIRTUAL] end {}", taskId);
    }

    @Transactional
    public void saveLog(String type, int taskId) {
        repository.save(new AsyncTaskLog(type, taskId));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

## NonBlocking 요청 컨트롤러

```java
@RestController
@RequestMapping("/non-blocking")
@RequiredArgsConstructor
public class NonBlockingController {

    private final NonBlockingService service;

    /**
     * 방식 1: @Async + 무제한 큐
     * - 모든 작업이 큐에 들어감 (거부 없음)
     * - 즉시 응답
     */
    @GetMapping("/unbounded-queue")
    public String unboundedQueue() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            service.processWithUnboundedQueue(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[무제한 큐 + @Async] 즉시 응답 - 소요시간: %dms, 100개 작업 큐에 추가됨", elapsed);
    }

    /**
     * 방식 2: CompletableFuture
     * - 직접 비동기 실행
     * - 즉시 응답
     */
    @GetMapping("/completable-future")
    public String completableFuture() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            service.processWithCompletableFuture(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[CompletableFuture] 즉시 응답 - 소요시간: %dms, 100개 작업 실행 중", elapsed);
    }

    /**
     * 방식 3: Virtual Thread (Java 21+)
     * - 경량 가상 스레드로 대량 동시 처리
     * - 즉시 응답
     */
    @GetMapping("/virtual-thread")
    public String virtualThread() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            service.processWithVirtualThread(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[Virtual Thread] 즉시 응답 - 소요시간: %dms, 100개 가상 스레드 실행 중", elapsed);
    }

    /**
     * 방식 4: @Async + Virtual Thread
     * - 스프링 @Async와 Virtual Thread 조합
     * - 즉시 응답
     */
    @GetMapping("/async-virtual")
    public String asyncVirtual() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            service.processAsyncWithVirtualThread(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[@Async + Virtual Thread] 즉시 응답 - 소요시간: %dms, 100개 작업 실행 중", elapsed);
    }
}
```

## 실행 결과

### 방식 1: `@Async` + 무제한 큐

<!-- TODO: 스크린샷 - /non-blocking/unbounded-queue 호출 결과 (즉시 응답, 소요시간 수 ms) -->

호출 결과

<!-- TODO: 스크린샷 - DB 저장 결과 (100개 모두 저장됨, UNBOUNDED-QUEUE 타입) -->

저장 결과

- 즉시 응답 (수 ms)
- 100개 작업 모두 큐에 적재 후 순차 처리
- 메모리 사용량 주의 필요

### 방식 2: `CompletableFuture`

<!-- TODO: 스크린샷 - /non-blocking/completable-future 호출 결과 -->

호출 결과

<!-- TODO: 스크린샷 - DB 저장 결과 (100개 모두 저장됨, COMPLETABLE-FUTURE 타입) -->

저장 결과

- 즉시 응답 (수 ms)
- 10개 스레드 풀에서 병렬 처리
- `thenApply`, `thenCompose` 등 체이닝 가능

### 방식 3: Virtual Thread (Java 21+)

<!-- TODO: 스크린샷 - /non-blocking/virtual-thread 호출 결과 -->

호출 결과

<!-- TODO: 스크린샷 - DB 저장 결과 (100개 모두 저장됨, VIRTUAL-THREAD 타입) -->

저장 결과

- 즉시 응답 (수 ms)
- 100개 가상 스레드가 동시에 실행
- 플랫폼 스레드 대비 매우 경량 (수백만 개 생성 가능)

### 방식 4: `@Async` + Virtual Thread

<!-- TODO: 스크린샷 - /non-blocking/async-virtual 호출 결과 -->

호출 결과

<!-- TODO: 스크린샷 - DB 저장 결과 (100개 모두 저장됨, ASYNC-VIRTUAL 타입) -->

저장 결과

- 즉시 응답 (수 ms)
- 스프링 `@Async`와 Virtual Thread의 장점 결합
- `@Transactional` 등 스프링 기능과 자연스럽게 통합

## 방식별 비교

| 구분 | 무제한 큐 | CompletableFuture | Virtual Thread | @Async + Virtual |
| --- | --- | --- | --- | --- |
| 즉시 응답 | O | O | O | O |
| 작업 유실 | X | X | X | X |
| 동시 실행 수 | 스레드 풀 크기 | 스레드 풀 크기 | 무제한 | 무제한 |
| 메모리 효율 | 낮음 (큐 적재) | 보통 | 높음 | 높음 |
| 체이닝/조합 | X | O | O | X |
| 스프링 통합 | O | △ | △ | O |
| Java 버전 | 8+ | 8+ | 21+ | 21+ |

### 결론

> **완전 논블로킹 + 작업 유실 없음**을 달성하려면 **무제한 큐** 또는 **Virtual Thread**를 활용해야 한다. Java 21 이상이라면 **Virtual Thread**가 메모리 효율과 동시성 측면에서 가장 우수하다.
