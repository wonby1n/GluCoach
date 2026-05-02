package com.ssafy.s309.domain.health.service;

import com.ssafy.s309.domain.health.dto.StepRecordBatchRequest;
import com.ssafy.s309.domain.health.dto.StepRecordBatchResponse;
import com.ssafy.s309.domain.health.repository.StepRecordBatchInserter;
import com.ssafy.s309.domain.health.repository.StepRecordBatchInserter.BatchInsertResult;
import com.ssafy.s309.domain.health.repository.StepRecordBatchInserter.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StepRecordService {

  private final StepRecordBatchInserter batchInserter;

  @Transactional
  public StepRecordBatchResponse saveBatch(Integer userId, StepRecordBatchRequest request) {
    var items =
        request.items().stream().map(i -> new Item(i.recordedAt(), i.stepsTotal())).toList();
    BatchInsertResult result = batchInserter.batchInsert(userId, items);
    return new StepRecordBatchResponse(result.inserted(), result.skipped());
  }
}
