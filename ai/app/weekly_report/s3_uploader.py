"""주간 보고서 PDF S3 업로드 모듈."""

import logging

import boto3
from botocore.exceptions import ClientError

from app.core.config import settings

log = logging.getLogger(__name__)


def _get_s3_client():
    kwargs = dict(
        region_name=settings.aws_region,
        aws_access_key_id=settings.aws_access_key_id,
        aws_secret_access_key=settings.aws_secret_access_key,
    )
    if settings.aws_s3_endpoint:
        kwargs["endpoint_url"] = settings.aws_s3_endpoint
    return boto3.client("s3", **kwargs)


def upload_pdf(pdf_bytes: bytes, user_id: int, week_start: str) -> str:
    """
    PDF를 S3에 업로드하고 S3 키를 반환한다.

    S3 키 형식: reports/{user_id}/week_{week_start}.pdf
    예: reports/1/week_2025-01-01.pdf

    Args:
        pdf_bytes: PDF 바이너리
        user_id: 사용자 ID
        week_start: 주 시작일 ("2025-01-01")

    Returns:
        S3 key 문자열

    Raises:
        RuntimeError: S3 설정 없음 또는 업로드 실패
    """
    if not settings.aws_s3_bucket:
        raise RuntimeError("AWS_S3_BUCKET 환경 변수가 설정되지 않음")

    key = f"reports/{user_id}/week_{week_start}.pdf"

    try:
        client = _get_s3_client()
        client.put_object(
            Bucket=settings.aws_s3_bucket,
            Key=key,
            Body=pdf_bytes,
            ContentType="application/pdf",
        )
        log.info("PDF 업로드 완료: bucket=%s key=%s size=%d bytes",
                 settings.aws_s3_bucket, key, len(pdf_bytes))
        return key

    except ClientError as e:
        log.error("S3 업로드 실패: %s", e)
        raise RuntimeError(f"S3 업로드 실패: {e}") from e
