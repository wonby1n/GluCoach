"""사용자 command 발화 webhook.

BE ChatMessageController.command → AiAgentCommandClient.dispatchAsync → POST /agent/command
"""

import logging
from typing import Any, Optional

from fastapi import APIRouter, BackgroundTasks
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel, Field

from app.agent.calendar_agent_run import run_calendar_reminder_agent
from app.agent.food_recommend_agent_run import run_food_recommend_agent
from app.agent.postmeal_agent_run import run_postmeal_agent

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


async def _run_food_recommend(user_id: int, chat_message_id: int, payload: dict):
    try:
        await run_in_threadpool(
            run_food_recommend_agent,
            user_id=user_id,
            parent_chat_message_id=chat_message_id,
            payload=payload,
        )
    except Exception as e:
        log.exception("food recommend agent failed: userId=%s err=%s", user_id, e)


async def _run_user_response(user_id: int, payload: dict):
    trigger = {"reason": "user_response", "meal_time": "", "user_reply": payload.get("user_reply", "")}
    try:
        await run_in_threadpool(run_postmeal_agent, trigger, user_id=user_id, alert_type="AGENT_MEAL_REPLY")
    except Exception as e:
        log.exception("user_response agent failed: userId=%s err=%s", user_id, e)


async def _run_calendar_reminder(user_id: int, chat_message_id: int, payload: dict):
    try:
        await run_in_threadpool(
            run_calendar_reminder_agent,
            user_id=user_id,
            parent_chat_message_id=chat_message_id,
            payload=payload,
        )
    except Exception as e:
        log.exception("calendar reminder agent failed: userId=%s err=%s", user_id, e)


@router.post("/command", response_model=AgentCommandResponse)
async def dispatch_command(req: AgentCommandRequest, background_tasks: BackgroundTasks) -> AgentCommandResponse:
    log.info(
        "command received: type=%s userId=%s chatMessageId=%s",
        req.command_type, req.user_id, req.chat_message_id,
    )

    if req.command_type == "recommend_food":
        background_tasks.add_task(_run_food_recommend, req.user_id, req.chat_message_id, req.payload)
        return AgentCommandResponse(status="accepted", command_type=req.command_type)

    if req.command_type == "user_response":
        background_tasks.add_task(_run_user_response, req.user_id, req.payload)
        return AgentCommandResponse(status="accepted", command_type=req.command_type)

    if req.command_type == "calendar_reminder":
        background_tasks.add_task(_run_calendar_reminder, req.user_id, req.chat_message_id, req.payload)
        return AgentCommandResponse(status="accepted", command_type=req.command_type)

    log.warning("unknown command_type: %s", req.command_type)
    return AgentCommandResponse(
        status="error",
        command_type=req.command_type,
        error=f"unknown_command_type: {req.command_type}",
    )
