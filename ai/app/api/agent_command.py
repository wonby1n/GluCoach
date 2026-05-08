"""사용자 command 발화 webhook.

BE ChatMessageController.command → AiAgentCommandClient.dispatchAsync → POST /agent/command
"""

import logging
from typing import Any, Optional

from fastapi import APIRouter
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel, Field

from app.agent.food_recommend_agent_run import run_food_recommend_agent

log = logging.getLogger(__name__)

router = APIRouter(prefix="/agent", tags=["AgentCommand"])


class AgentCommandRequest(BaseModel):
    user_id: int = Field(..., description="사용자 ID")
    chat_message_id: int = Field(..., description="user command가 INSERT된 chat_messages.id (parent로 사용)")
    command_type: str = Field(..., description="recommend_food / ask_glucose 등")
    payload: dict[str, Any] = Field(default_factory=dict)


class AgentCommandResponse(BaseModel):
    status: str
    command_type: str
    message: Optional[str] = None
    turns: Optional[int] = None
    error: Optional[str] = None


@router.post("/command", response_model=AgentCommandResponse)
async def dispatch_command(req: AgentCommandRequest) -> AgentCommandResponse:
    log.info(
        "command received: type=%s userId=%s chatMessageId=%s",
        req.command_type, req.user_id, req.chat_message_id,
    )

    if req.command_type == "recommend_food":
        try:
            result = await run_in_threadpool(
                run_food_recommend_agent,
                user_id=req.user_id,
                parent_chat_message_id=req.chat_message_id,
                payload=req.payload,
            )
        except Exception as e:
            log.exception("food recommend agent failed")
            return AgentCommandResponse(
                status="error",
                command_type=req.command_type,
                error=f"agent_execution_failed: {type(e).__name__}",
            )

        status = "fallback" if result.get("error") == "llm_call_failed" else (
            "error" if result.get("error") else "success"
        )
        return AgentCommandResponse(
            status=status,
            command_type=req.command_type,
            message=result.get("message"),
            turns=result.get("turns"),
            error=result.get("error"),
        )

    log.warning("unknown command_type: %s", req.command_type)
    return AgentCommandResponse(
        status="error",
        command_type=req.command_type,
        error=f"unknown_command_type: {req.command_type}",
    )
