package com.ssafy.s309.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 사용자가 채팅 화면에서 발화하는 명령(예: "음식 추천해줘" 버튼). sender='user', message_type=NULL, command_type=NOT NULL.
 *
 * <p>parent_id는 새 대화 시작 가정으로 NULL 저장. 후속 agent 응답이 INSERT 시 이 메시지의 id를 parent_id로 참조.
 *
 * <p>message: 사용자에게 보일 라벨 텍스트 (없으면 command_type을 그대로). payload: 명령 파라미터(예: 음식 카테고리 필터). 둘 다 선택.
 */
public record ChatCommandRequest(
    @NotBlank @Size(max = 50) String commandType,
    @Size(max = 500) String message,
    Map<String, Object> payload) {}
