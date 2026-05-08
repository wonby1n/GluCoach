package com.ssafy.s309.domain.cgm.service;

import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import com.ssafy.s309.domain.cgm.event.GlucoseReceivedEvent;
import com.ssafy.s309.domain.cgm.repository.GlucoseRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CgmService {

  private final GlucoseRecordRepository repo;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public GlucoseRecord save(Integer userId, BigDecimal value, LocalDateTime measuredAt) {
    GlucoseRecord record =
        repo.save(
            GlucoseRecord.builder().userId(userId).value(value).measuredAt(measuredAt).build());
    // 트랜잭션 커밋 후 @TransactionalEventListener(AFTER_COMMIT) 리스너가 수신
    eventPublisher.publishEvent(new GlucoseReceivedEvent(userId, value, record.getId()));
    return record;
  }

  @Transactional(readOnly = true)
  public List<GlucoseRecord> findRange(Integer userId, LocalDateTime from, LocalDateTime to) {
    return repo.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(userId, from, to);
  }
}
