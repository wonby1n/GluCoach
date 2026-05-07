"""주간 보고서 생성 오케스트레이터.

흐름:
  1. LLM 호출 → ai_summary, ai_suggest 생성
  2. PDF 생성 (matplotlib)
  3. S3 업로드 → pdf_key 반환
"""

import logging

from app.schemas.report import WeeklyReportRequest, WeeklyReportResponse
from app.report.llm_caller import call_weekly_report_llm
from app.report.pdf_generator import generate_pdf
from app.report.s3_uploader import upload_pdf

log = logging.getLogger(__name__)


def run_weekly_report(req: WeeklyReportRequest) -> WeeklyReportResponse:
    """
    주간 보고서 생성 전체 흐름을 실행한다.

    LLM 호출 / PDF 생성 / S3 업로드 중 하나라도 실패하면 error 반환.
    """
    log.info("주간 보고서 생성 시작: user_id=%d week=%s~%s",
             req.user_id, req.week_start, req.week_end)

    # ── Step 1: LLM 호출 ─────────────────────────────────────────────
    try:
        ai_summary, ai_suggest = call_weekly_report_llm(req)
        log.info("LLM 생성 완료: summary_len=%d suggest_len=%d",
                 len(ai_summary), len(ai_suggest))
    except Exception as e:
        log.error("LLM 호출 예외: %s", e)
        return WeeklyReportResponse(status="error", error=f"llm_failed: {e}")

    # ── Step 2: PDF 생성 ─────────────────────────────────────────────
    try:
        pdf_bytes = generate_pdf(req, ai_summary, ai_suggest)
        log.info("PDF 생성 완료: size=%d bytes", len(pdf_bytes))
    except Exception as e:
        log.error("PDF 생성 예외: %s", e)
        return WeeklyReportResponse(status="error", error=f"pdf_failed: {e}")

    # ── Step 3: S3 업로드 ────────────────────────────────────────────
    try:
        pdf_key = upload_pdf(pdf_bytes, req.user_id, req.week_start)
        log.info("S3 업로드 완료: pdf_key=%s", pdf_key)
    except Exception as e:
        log.error("S3 업로드 예외: %s", e)
        return WeeklyReportResponse(status="error", error=f"s3_failed: {e}")

    return WeeklyReportResponse(
        status="success",
        ai_summary=ai_summary,
        ai_suggest=ai_suggest,
        pdf_key=pdf_key,
    )
