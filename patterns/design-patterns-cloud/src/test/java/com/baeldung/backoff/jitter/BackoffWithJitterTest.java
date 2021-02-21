package com.baeldung.backoff.jitter;

import io.github.resilience4j.retry.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static com.baeldung.backoff.jitter.BackoffWithJitterTest.RetryProperties.*;
import static io.github.resilience4j.retry.IntervalFunction.ofExponentialBackoff;
import static io.github.resilience4j.retry.IntervalFunction.ofExponentialRandomBackoff;
import static java.util.Collections.nCopies;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class BackoffWithJitterTest {

    static Logger log = LoggerFactory.getLogger(BackoffWithJitterTest.class);

    interface PingPongService {

        String call(String ping) throws PingPongServiceException;
    }

    class PingPongServiceException extends RuntimeException {

        public PingPongServiceException(String reason) {
            super(reason);
        }
    }

    private PingPongService service;
    private static final int NUM_CONCURRENT_CLIENTS = 4;

    @Before
    public void setUp() {
        service = mock(PingPongService.class);
    }

    @Test
    public void whenRetryExponentialBackoff_thenRetriedConfiguredNoOfTimes() {
        IntervalFunction intervalFn = ofExponentialBackoff(INITIAL_INTERVAL, MULTIPLIER);
        Function<String, String> pingPongFn = getRetryablePingPongFn(intervalFn);
        // 模拟抛出异常
        when(service.call(anyString())).thenThrow(PingPongServiceException.class);
        // 调用执行业务逻辑
        try {
            pingPongFn.apply("Hello");
        } catch (PingPongServiceException e) {
            verify(service, times(MAX_RETRIES)).call(anyString());
        }
        // interspersed periods of work with more idle time. 把活分散到一个大的时间段去做
        // 注意观察重试的间隔按照 wait_interval = base * multiplier^n 公式计算获得，获得的等待时长是渐进式增长的
        // 第一次直接执行
        // 第二次间隔1秒执行 1000*2^0=1000
        // 第三次间隔2秒执行 1000*2^1=2000
        // 第四次间隔4秒执行 1000*2^2=4000
    }

    @Test
    public void whenRetryExponentialBackoffWithoutJitter_thenThunderingHerdProblemOccurs() throws InterruptedException {
        // 多个线程一起调用，每个线程重试的触发时间也基本一致，产生资源竞争问题，需要考虑把重试时间再打散
        IntervalFunction intervalFn = ofExponentialBackoff(INITIAL_INTERVAL, MULTIPLIER);
        test(intervalFn);
    }

    @Test
    public void whenRetryExponentialBackoffWithJitter_thenRetriesAreSpread() throws InterruptedException {
        // Jitter给间隔时间添加了一点随机性，使得多个线程同步调用情况下，retry的触发时间可以错开，分布得更平滑
        // 下面是添加了随机性的时间间隔函数，增加了随机因子
        IntervalFunction intervalFn = ofExponentialRandomBackoff(INITIAL_INTERVAL, MULTIPLIER, RANDOMIZATION_FACTOR);
        test(intervalFn);
        // 这样就减少了冲突和空闲时间，每个时间段基本上都有常量比例的方法执行，而不是波浪式的调用，让计算资源使用更加高效
    }

    /**
     * 多线程模拟多个客户端调用
     * @param intervalFn
     * @throws InterruptedException
     */
    private void test(IntervalFunction intervalFn) throws InterruptedException {
        Function<String, String> pingPongFn = getRetryablePingPongFn(intervalFn);
        ExecutorService executors = newFixedThreadPool(NUM_CONCURRENT_CLIENTS);
        List<Callable<String>> tasks = nCopies(NUM_CONCURRENT_CLIENTS, () -> pingPongFn.apply("Hello"));

        when(service.call(anyString())).thenThrow(PingPongServiceException.class);

        executors.invokeAll(tasks);
    }

    /**
     * 产生带重试的业务逻辑函数
     * @param intervalFn 封装了间隔策略的函数
     * @return 带重试的业务逻辑函数
     */
    private Function<String, String> getRetryablePingPongFn(IntervalFunction intervalFn) {
        // 构造retry对象
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(MAX_RETRIES)
                .intervalFunction(intervalFn)
                .retryExceptions(PingPongServiceException.class)
                .build();
        Retry retry = Retry.of("pingpong", retryConfig);
        // 把业务逻辑添加到retry对象
        return Retry.decorateFunction(retry, ping -> {
            log.info("Invoked at {}", LocalDateTime.now());
            return service.call(ping);
        });
    }

    static class RetryProperties {
        static final Long INITIAL_INTERVAL = 1000L;
        static final Double MULTIPLIER = 2.0D;
        static final Double RANDOMIZATION_FACTOR = 0.6D;
        static final Integer MAX_RETRIES = 4;
    }
}
