"""주간 보고서 API 라우터.

POST /report/weekly  : BE 스케줄러가 호출하는 보고서 생성 엔드포인트.
  - 집계 데이터(WeeklyReportRequest)를 받아
  - LLM 호출 → PDF 생성 후
  - {ai_summary, ai_suggest, pdf_bytes(base64)}를 반환한다.
  - S3 업로드는 백엔드가 담당한다.
"""

import logging

from fastapi import APIRouter
from fastapi.concurrency import run_in_threadpool

from app.schemas.report import WeeklyReportRequest, WeeklyReportResponse
from app.weekly_report.runner import run_weekly_report

log = logging.getLogger(__name__)

router = APIRouter(prefix="/report", tags=["Report"])


@router.post("/weekly", response_model=WeeklyReportResponse)
async def generate_weekly_report(req: WeeklyReportRequest):
    """
    주간 보고서를 생성한다.

    BE 스케줄러가 주간 데이터 집계 후 호출.
    LLM + PDF 생성은 블로킹 작업이므로 threadpool에서 실행.
    """
    log.info("POST /report/weekly user_id=%d week=%s", req.user_id, req.week_start)
    try:
        result = await run_in_threadpool(run_weekly_report, req)
    except Exception as e:
        log.error("보고서 생성 예외: %s", e)
        return WeeklyReportResponse(status="error", error=str(e))
    return result
