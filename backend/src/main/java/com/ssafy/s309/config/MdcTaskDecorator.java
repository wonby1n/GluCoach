package com.ssafy.s309.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * 비동기 실행 시 호출자 스레드의 MDC 컨텍스트를 워커 스레드로 전파.
 *
 * <p>ThreadPoolTaskExecutor / fork-join 워커는 자체 MDC 가 비어 있어 correlationId 등 추적 메타가 끊긴다. decorate
 * 시점(호출 스레드)의 MDC 스냅샷을 캡처 → 워커 진입 시 setContextMap → 종료 시 clear 로 풀 재사용 시 워커에 잔류값이 새는 것을 방지.
 */
public class MdcTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    Map<String, String> snapshot = MDC.getCopyOfContextMap();
    return () -> {
      try {
        if (snapshot != null) {
          MDC.setContextMap(snapshot);
        }
        runnable.run();
      } finally {
        MDC.clear();
      }
    };
  }
}
