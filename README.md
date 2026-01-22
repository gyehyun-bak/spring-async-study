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
