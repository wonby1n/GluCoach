"""foods.display_name LLM 정제 스크립트.

V15(base 추출) / V16(수기 보정) / V17(자동 도치) 이후에도 raw '_' 가 display_name 에
노출되는 row 를 Claude Haiku 로 일괄 정제해 V18 마이그레이션 SQL 을 생성한다.

대상: display_name 이 V15 의 base 결과 그대로인 row 만 — V16/V17 로 이미 보정된
row 는 LLM 이 다시 손대지 않도록 dump 단계에서 제외.

흐름:
  1. SSH 로 prod foods 를 CSV 로 dump (별도 명령, 본 스크립트 입력)
  2. 본 스크립트가 CSV 읽어 BATCH_SIZE 단위로 Claude Haiku 호출
  3. 응답을 자동 검증 후 V18 SQL 작성

사용법:
  cd ai/
  export ANTHROPIC_API_KEY=sk-ant-...
  uv run python scripts/refine_display_names.py \\
      --input foods_dump.csv \\
      --output ../backend/src/main/resources/db/migration/V18__llm_refined_display_names.sql

옵션:
  --dry-run        LLM 호출 없이 system prompt + 입력 샘플만 출력
  --limit N        처음 N row 만 처리 (스모크 테스트용)
  --batch-size N   배치 크기 (default 50)
"""

from __future__ import annotations

import argparse
import csv
import json
import logging
import os
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path

import anthropic

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)

MODEL = "claude-haiku-4-5-20251001"
BATCH_SIZE = 50
MAX_TOKENS_PER_BATCH = 4096
RETRY_DELAYS = [1, 2, 4, 8]
NAME_PATTERN = re.compile(r"^[가-힣A-Za-z0-9 ()·\-&+/.,]+$")
MAX_LEN = 100


@dataclass
class FoodRow:
    id: int
    name: str
    current_display: str


SYSTEM_PROMPT = """당신은 한국 음식명을 정제하는 도우미입니다.

입력: 식약처 영양정보 DB 의 음식명 목록 (각 항목 = {id, name, current_display}).
- name 은 raw 식별자 (예: "김밥_샐러리", "라면_김치", "갈비탕_소금제외").
- current_display 는 현재 표시명 (대개 첫 토큰만 잘린 base).

각 항목에 대해 한국 사용자에게 자연스럽게 노출할 한국어 메뉴명을 결정합니다.

규칙:
  1. 도치형(예: 김밥_샐러리) → 도치한 자연 메뉴명(샐러리김밥).
  2. modifier 가 두 토큰 이상이면 합쳐서 도치 (김밥_참치_김치 → 참치김치김밥).
  3. 수식/조리 옵션(예: 갈비탕_소금제외, 김치찌개_매운맛) → base 만 유지(갈비탕, 김치찌개).
  4. 종류/부분 표기(예: 라면_국물, 라면_면, 라면_라면만) → base 유지(라면).
  5. modifier 안에 base 가 또 포함되면 modifier 자체를 사용(초밥_유부초밥 → 유부초밥).
  6. 100자 이하 한글/영숫자/괄호/공백/하이픈/middle dot(·) 만 사용. 언더스코어 절대 금지.
  7. 이미 자연스러운 current_display 면 그대로 반환.

응답은 반드시 다음 JSON 만 (다른 텍스트 절대 금지):
{
  "results": [
    {"id": 1039, "display": "샐러리김밥"},
    {"id": 3627, "display": "라면"}
  ]
}"""


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--input", required=True, type=Path, help="dump CSV path")
    p.add_argument("--output", required=True, type=Path, help="V18 SQL output path")
    p.add_argument("--batch-size", type=int, default=BATCH_SIZE)
    p.add_argument("--dry-run", action="store_true", help="LLM 호출 없이 prompt + 샘플 출력")
    p.add_argument("--limit", type=int, default=None, help="처음 N row 만 처리")
    return p.parse_args()


def read_csv(path: Path) -> list[FoodRow]:
    """CSV 읽기. psql -A -F',' -t 출력 형식(헤더 없음) 또는 헤더 있는 형식 둘 다 허용."""
    rows: list[FoodRow] = []
    with path.open(encoding="utf-8") as f:
        reader = csv.reader(f)
        for r in reader:
            if len(r) < 3:
                continue
            try:
                fid = int(r[0])
            except ValueError:
                # 헤더 줄
                continue
            rows.append(FoodRow(id=fid, name=r[1].strip(), current_display=r[2].strip()))
    log.info("CSV 에서 %d row 로드", len(rows))
    return rows


def call_batch(client: anthropic.Anthropic, batch: list[FoodRow]) -> dict[int, str]:
    user_payload = json.dumps(
        [{"id": r.id, "name": r.name, "current_display": r.current_display} for r in batch],
        ensure_ascii=False,
    )
    last_err: Exception | None = None
    for attempt in range(len(RETRY_DELAYS) + 1):
        try:
            resp = client.messages.create(
                model=MODEL,
                max_tokens=MAX_TOKENS_PER_BATCH,
                system=SYSTEM_PROMPT,
                messages=[{"role": "user", "content": user_payload}],
            )
            text = resp.content[0].text.strip()
            # 응답이 ```json ... ``` 로 감싸진 경우 대비
            if text.startswith("```"):
                text = re.sub(r"^```(?:json)?\s*", "", text)
                text = re.sub(r"\s*```$", "", text)
            data = json.loads(text)
            return {int(item["id"]): str(item["display"]).strip() for item in data["results"]}
        except (anthropic.APIStatusError, anthropic.APIConnectionError,
                anthropic.APITimeoutError, json.JSONDecodeError, KeyError, ValueError) as e:
            last_err = e
            if attempt >= len(RETRY_DELAYS):
                break
            delay = RETRY_DELAYS[attempt]
            log.warning("배치 호출 실패 (%s), %ds 후 재시도", type(e).__name__, delay)
            time.sleep(delay)
    log.error("배치 실패 (재시도 소진): %s", last_err)
    return {}


def validate(refined: str) -> tuple[bool, str]:
    """검증 통과 시 (True, ''), 실패 시 (False, reason)."""
    if not refined:
        return False, "empty"
    if len(refined) > MAX_LEN:
        return False, f"len>{MAX_LEN}"
    if "_" in refined:
        return False, "contains _"
    if not NAME_PATTERN.match(refined):
        return False, "non-allowed chars"
    return True, ""


def write_sql(out: Path, refined: dict[int, str], skipped: list[tuple[int, str, str]]) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8") as f:
        f.write(
            "-- foods.display_name LLM 정제 결과 (Claude Haiku 4.5).\n"
            "-- V15(base) / V16(수기) / V17(자동 도치) 이후에도 _ 가 노출되던 row 를\n"
            "-- LLM 으로 자연스러운 한국어 메뉴명으로 일괄 정제. 자동 검증 통과 row 만 포함.\n"
            f"-- 적용 row: {len(refined)}건 / 검증 스킵: {len(skipped)}건\n\n"
        )
        for fid in sorted(refined):
            display = refined[fid].replace("'", "''")
            f.write(f"UPDATE foods SET display_name = '{display}' WHERE id = {fid};\n")
    log.info("SQL 작성 완료: %s (적용=%d, 스킵=%d)", out, len(refined), len(skipped))


def main() -> int:
    args = parse_args()

    rows = read_csv(args.input)
    if args.limit:
        rows = rows[: args.limit]
        log.info("--limit %d 적용 → %d row 만 처리", args.limit, len(rows))

    if args.dry_run:
        print("=== SYSTEM PROMPT ===")
        print(SYSTEM_PROMPT)
        print("\n=== 입력 샘플 (앞 5 row) ===")
        print(json.dumps(
            [{"id": r.id, "name": r.name, "current_display": r.current_display} for r in rows[:5]],
            ensure_ascii=False, indent=2,
        ))
        return 0

    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        log.error("ANTHROPIC_API_KEY 환경변수 필요")
        return 1
    client = anthropic.Anthropic(api_key=api_key)

    refined: dict[int, str] = {}
    skipped: list[tuple[int, str, str]] = []
    n_batches = (len(rows) + args.batch_size - 1) // args.batch_size

    for i in range(0, len(rows), args.batch_size):
        batch = rows[i : i + args.batch_size]
        b_idx = i // args.batch_size + 1
        log.info("배치 %d/%d (%d row)", b_idx, n_batches, len(batch))
        result = call_batch(client, batch)

        for row in batch:
            cand = result.get(row.id)
            if cand is None:
                skipped.append((row.id, "", "LLM 응답 누락"))
                continue
            ok, reason = validate(cand)
            if not ok:
                skipped.append((row.id, cand, reason))
                continue
            if cand == row.current_display:
                continue  # 변경 불필요
            refined[row.id] = cand

    write_sql(args.output, refined, skipped)

    if skipped:
        log.warning("스킵 %d건 — 앞 20건 표본:", len(skipped))
        for fid, cand, reason in skipped[:20]:
            log.warning("  id=%s reason=%s cand=%r", fid, reason, cand)

    return 0


if __name__ == "__main__":
    sys.exit(main())
