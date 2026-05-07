package com.ssafy.s309.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chat_messages")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessage {

  public static final String SENDER_AGENT = "agent";
  public static final String SENDER_SYSTEM = "system";
  public static final String SENDER_USER = "user";

  public static final String SOURCE_BE = "be";
  public static final String SOURCE_AGENT = "agent";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "sender", nullable = false, length = 10)
  private String sender;

  @Column(name = "message", nullable = false, columnDefinition = "TEXT")
  private String message;

  @Column(name = "display_trace", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> displayTrace;

  @Column(name = "options", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private List<Map<String, String>> options;

  @Column(name = "alert_type", length = 50)
  private String alertType;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @Column(name = "is_read", nullable = false)
  @Builder.Default
  private Boolean isRead = false;

  @Column(name = "source", length = 20)
  private String source;

  @Column(name = "parent_id")
  private Long parentId;

  @Column(name = "selected_option_id", length = 50)
  private String selectedOptionId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public void resolve() {
    this.resolvedAt = LocalDateTime.now();
  }

  public void markRead() {
    this.isRead = true;
  }
}
