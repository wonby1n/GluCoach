package com.ssafy.s309.domain.health.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * step_records 배치 INSERT 전용 컴포넌트.
 *
 * <p>Hibernate {@code saveAll}은 batch 내 중복 1건이 발생하면 전체 트랜잭션이 실패한다. 이를 회피하기 위해 Postgres {@code
 * INSERT ... ON CONFLICT DO NOTHING}을 JdbcTemplate batchUpdate로 실행한다.
 *
 * <p>반환값은 실제로 INSERT된 row 수의 합. {@code skipped = items.size() - inserted}.
 */
@Repository
@RequiredArgsConstructor
public class StepRecordBatchInserter {

  private static final String INSERT_SQL =
      """
      INSERT INTO step_records (user_id, recorded_at, steps_total)
      VALUES (?, ?, ?)
      ON CONFLICT (user_id, recorded_at) DO NOTHING
      """;

  private final JdbcTemplate jdbcTemplate;

  public BatchInsertResult batchInsert(Integer userId, List<Item> items) {
    int[] affected =
        jdbcTemplate.batchUpdate(
            INSERT_SQL,
            new BatchPreparedStatementSetter() {
              @Override
              public void setValues(PreparedStatement ps, int i) throws SQLException {
                Item item = items.get(i);
                ps.setInt(1, userId);
                ps.setTimestamp(2, Timestamp.valueOf(item.recordedAt()));
                ps.setInt(3, item.stepsTotal());
              }

              @Override
              public int getBatchSize() {
                return items.size();
              }
            });

    int inserted = 0;
    for (int n : affected) {
      // ON CONFLICT DO NOTHING은 충돌 시 0, 신규 INSERT는 1 반환
      if (n > 0) inserted += n;
    }
    int skipped = items.size() - inserted;
    return new BatchInsertResult(inserted, skipped);
  }

  public record Item(LocalDateTime recordedAt, Integer stepsTotal) {}

  public record BatchInsertResult(int inserted, int skipped) {}
}
