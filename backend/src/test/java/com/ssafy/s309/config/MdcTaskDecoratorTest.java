package com.ssafy.s309.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

@SuppressWarnings("NonAsciiCharacters")
class MdcTaskDecoratorTest {

  private final MdcTaskDecorator decorator = new MdcTaskDecorator();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void 호출자_MDC가_워커_스레드에서_보임() throws InterruptedException {
    MDC.put("correlationId", "abc-123");
    AtomicReference<String> seen = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    Runnable decorated =
        decorator.decorate(
            () -> {
              seen.set(MDC.get("correlationId"));
              latch.countDown();
            });

    Thread t = new Thread(decorated);
    t.start();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(seen.get()).isEqualTo("abc-123");
  }

  @Test
  void 워커_스레드_MDC는_실행_종료_후_clear됨() throws InterruptedException {
    MDC.put("correlationId", "abc-123");
    AtomicReference<String> afterRun = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    Runnable decorated = decorator.decorate(() -> {});

    Thread t =
        new Thread(
            () -> {
              decorated.run();
              afterRun.set(MDC.get("correlationId"));
              latch.countDown();
            });
    t.start();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(afterRun.get()).isNull();
  }

  @Test
  void 워커_runnable이_예외_던져도_MDC_clear됨() throws InterruptedException {
    MDC.put("correlationId", "abc-123");
    AtomicReference<String> afterRun = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    Runnable decorated =
        decorator.decorate(
            () -> {
              throw new IllegalStateException("boom");
            });

    Thread t =
        new Thread(
            () -> {
              try {
                decorated.run();
              } catch (RuntimeException ignored) {
                // expected
              }
              afterRun.set(MDC.get("correlationId"));
              latch.countDown();
            });
    t.start();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(afterRun.get()).isNull();
  }

  @Test
  void 호출자_MDC가_비어있어도_NPE_없이_실행() {
    MDC.clear();
    Runnable decorated = decorator.decorate(() -> {});
    assertThatCode(decorated::run).doesNotThrowAnyException();
  }

  @Test
  void 호출자_MDC와_워커_기존_MDC가_다른_경우_워커는_스냅샷으로_덮어씀() throws InterruptedException {
    // 풀 재사용 시 워커에 이전 task 잔류값이 있어도 caller snapshot 으로 정확히 갈음되는지.
    MDC.put("correlationId", "caller-1");
    AtomicReference<String> seen = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    Runnable decorated =
        decorator.decorate(
            () -> {
              seen.set(MDC.get("correlationId"));
              latch.countDown();
            });

    Thread t =
        new Thread(
            () -> {
              MDC.put("correlationId", "stale-worker-value");
              decorated.run();
            });
    t.start();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(seen.get()).isEqualTo("caller-1");
  }
}
