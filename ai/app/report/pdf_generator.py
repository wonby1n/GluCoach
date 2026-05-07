"""주간 보고서 PDF 생성 모듈.

matplotlib PdfPages로 A4 4페이지 PDF를 생성한다.
  Page 1: 표지 + 핵심 지표 + TIR 파이차트
  Page 2: 주간 혈당 추이 선 그래프
  Page 3: 시간대별 혈당 패턴 막대 그래프
  Page 4: AI 요약 및 코칭 제안 텍스트

한글 폰트: Docker 환경에서 fonts-noto-cjk 설치 후 사용.
로컬 환경에서는 시스템 한글 폰트(맑은 고딕, AppleGothic 등)를 자동 탐색.
"""

import io
import logging
import textwrap
from datetime import datetime

import matplotlib
import matplotlib.font_manager as fm
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.backends.backend_pdf import PdfPages

from app.schemas.report import WeeklyReportRequest

log = logging.getLogger(__name__)

# A4 사이즈 (인치)
A4_W, A4_H = 8.27, 11.69

# 색상 팔레트
COLOR_IN_RANGE = "#4CAF50"
COLOR_ABOVE = "#F44336"
COLOR_BELOW = "#FF9800"
COLOR_LINE = "#1565C0"
COLOR_FILL = "#90CAF9"
COLOR_TARGET = "#E8F5E9"

# 혈당 취약 시간대 기준 (평균 이상이면 강조)
HIGH_HOUR_THRESHOLD = 180


def _setup_korean_font() -> str | None:
    """사용 가능한 한글 폰트를 탐색해 matplotlib에 적용한다."""
    candidates = [
        "Noto Sans CJK KR",
        "NotoSansCJK-Regular",
        "Malgun Gothic",
        "AppleGothic",
        "NanumGothic",
        "Noto Sans KR",
    ]
    available = {f.name for f in fm.fontManager.ttflist}
    for name in candidates:
        if name in available:
            matplotlib.rcParams["font.family"] = name
            log.info("한글 폰트 적용: %s", name)
            return name

    # 파일 경로로 직접 탐색 (Docker fonts-noto-cjk 설치 경로)
    for f in fm.findSystemFonts(fontext="ttf"):
        if any(k in f for k in ("NotoSansCJK", "NotoSans", "Malgun", "Nanum")):
            prop = fm.FontProperties(fname=f)
            matplotlib.rcParams["font.family"] = prop.get_name()
            log.info("한글 폰트 파일 직접 적용: %s", f)
            return prop.get_name()

    log.warning("한글 폰트를 찾지 못함. 한글이 깨질 수 있음.")
    return None


def _wrap_text(text: str, width: int = 55) -> str:
    """긴 텍스트를 줄바꿈 처리한다."""
    lines = []
    for para in text.split("\n"):
        wrapped = textwrap.fill(para, width=width)
        lines.append(wrapped)
    return "\n".join(lines)


# ── Page 1: 표지 + 핵심 지표 + TIR 파이차트 ─────────────────────────

def _page_cover(pdf: PdfPages, req: WeeklyReportRequest) -> None:
    gmi = 3.31 + (0.02392 * req.avg_glucose)
    cv = (req.glucose_sd / req.avg_glucose * 100) if req.avg_glucose > 0 else 0

    fig = plt.figure(figsize=(A4_W, A4_H))
    fig.patch.set_facecolor("white")

    # ── 헤더 영역 ─────────────────────────────────────────────────────
    ax_header = fig.add_axes([0.05, 0.82, 0.9, 0.14])
    ax_header.set_axis_off()
    ax_header.set_facecolor("#1565C0")
    ax_header.add_patch(mpatches.FancyBboxPatch(
        (0, 0), 1, 1, boxstyle="round,pad=0", fc="#1565C0", ec="none",
        transform=ax_header.transAxes,
    ))
    ax_header.text(
        0.5, 0.65, "주간 혈당 관리 리포트",
        ha="center", va="center", fontsize=20, fontweight="bold",
        color="white", transform=ax_header.transAxes,
    )
    ax_header.text(
        0.5, 0.25, f"{req.user_name}님 | {req.week_start} ~ {req.week_end}",
        ha="center", va="center", fontsize=11,
        color="#BBDEFB", transform=ax_header.transAxes,
    )

    # ── 핵심 지표 카드 6개 ────────────────────────────────────────────
    metrics = [
        ("평균 혈당", f"{req.avg_glucose:.1f}", "mg/dL"),
        ("최저 / 최고", f"{req.min_glucose:.0f} / {req.max_glucose:.0f}", "mg/dL"),
        ("표준편차", f"{req.glucose_sd:.1f}", "mg/dL"),
        ("GMI (예상 HbA1c)", f"{gmi:.1f}", "%"),
        ("CV% (변동계수)", f"{cv:.1f}", "%"),
        ("식사 기록", f"{req.meal_count}", "회"),
    ]

    cols, rows = 3, 2
    card_w, card_h = 0.28, 0.11
    start_x, start_y = 0.05, 0.64
    gap_x, gap_y = 0.32, 0.14

    for i, (label, value, unit) in enumerate(metrics):
        col, row = i % cols, i // cols
        x = start_x + col * gap_x
        y = start_y - row * gap_y
        ax_c = fig.add_axes([x, y, card_w, card_h])
        ax_c.set_axis_off()
        ax_c.add_patch(mpatches.FancyBboxPatch(
            (0.03, 0.05), 0.94, 0.9,
            boxstyle="round,pad=0.02", fc="#F5F5F5", ec="#E0E0E0", linewidth=0.8,
            transform=ax_c.transAxes,
        ))
        ax_c.text(0.5, 0.75, label, ha="center", va="center",
                  fontsize=7.5, color="#757575", transform=ax_c.transAxes)
        ax_c.text(0.5, 0.38, value, ha="center", va="center",
                  fontsize=14, fontweight="bold", color="#212121", transform=ax_c.transAxes)
        ax_c.text(0.5, 0.1, unit, ha="center", va="center",
                  fontsize=7, color="#9E9E9E", transform=ax_c.transAxes)

    # ── TIR 파이차트 ──────────────────────────────────────────────────
    ax_pie = fig.add_axes([0.08, 0.05, 0.45, 0.42])
    sizes = [req.time_in_range, req.time_above_range, req.time_below_range]
    labels = [
        f"목표 범위\n{req.time_in_range:.1f}%",
        f"고혈당\n{req.time_above_range:.1f}%",
        f"저혈당\n{req.time_below_range:.1f}%",
    ]
    colors = [COLOR_IN_RANGE, COLOR_ABOVE, COLOR_BELOW]
    explode = (0.04, 0, 0)
    wedges, texts = ax_pie.pie(
        sizes, labels=labels, colors=colors, explode=explode,
        startangle=90, textprops={"fontsize": 8},
        wedgeprops={"linewidth": 1, "edgecolor": "white"},
    )
    ax_pie.set_title("혈당 분포 (TIR)", fontsize=10, fontweight="bold", pad=8)

    # ── TIR 목표 대비 범례 ────────────────────────────────────────────
    ax_legend = fig.add_axes([0.56, 0.05, 0.38, 0.42])
    ax_legend.set_axis_off()
    targets = [
        (COLOR_IN_RANGE, f"목표 범위 내 (TIR)", req.time_in_range, "≥ 70%"),
        (COLOR_ABOVE,    f"고혈당 (TAR)", req.time_above_range, "< 25%"),
        (COLOR_BELOW,    f"저혈당 (TBR)", req.time_below_range, "< 4%"),
    ]
    for idx, (color, name, value, target) in enumerate(targets):
        y_pos = 0.80 - idx * 0.28
        ax_legend.add_patch(mpatches.Rectangle(
            (0.0, y_pos - 0.04), 0.06, 0.12, fc=color, ec="none",
            transform=ax_legend.transAxes,
        ))
        ax_legend.text(0.12, y_pos + 0.02, name, fontsize=8,
                       va="center", transform=ax_legend.transAxes)
        ax_legend.text(0.12, y_pos - 0.08, f"{value:.1f}%  (권장: {target})",
                       fontsize=7, color="#757575", va="center", transform=ax_legend.transAxes)

    # ── 생성 시각 ─────────────────────────────────────────────────────
    fig.text(0.5, 0.02, f"생성: {datetime.now().strftime('%Y-%m-%d %H:%M')}",
             ha="center", fontsize=7, color="#9E9E9E")

    pdf.savefig(fig, bbox_inches="tight")
    plt.close(fig)


# ── Page 2: 주간 혈당 추이 선 그래프 ────────────────────────────────

def _page_weekly_trend(pdf: PdfPages, req: WeeklyReportRequest) -> None:
    if not req.daily_avg_glucose:
        return

    fig, ax = plt.subplots(figsize=(A4_W, A4_H))
    fig.patch.set_facecolor("white")

    dates = [d.date[5:] for d in req.daily_avg_glucose]   # "MM-DD"
    avgs = [d.avg for d in req.daily_avg_glucose]
    mins = [d.min for d in req.daily_avg_glucose]
    maxs = [d.max for d in req.daily_avg_glucose]
    x = list(range(len(dates)))

    # 목표 범위 배경
    ax.axhspan(req.target_low, req.target_high,
               alpha=0.12, color=COLOR_IN_RANGE, label="목표 범위")

    # 최저~최고 범위 음영
    ax.fill_between(x, mins, maxs, alpha=0.25, color=COLOR_FILL, label="최저~최고 범위")

    # 평균선
    ax.plot(x, avgs, "-o", color=COLOR_LINE, linewidth=2.5,
            markersize=7, label="일평균 혈당", zorder=3)

    # 수치 레이블
    for xi, avg in zip(x, avgs):
        ax.annotate(f"{avg:.0f}", (xi, avg), textcoords="offset points",
                    xytext=(0, 10), ha="center", fontsize=8, color=COLOR_LINE)

    # 목표 범위 경계선
    ax.axhline(req.target_high, color=COLOR_ABOVE, linewidth=0.8,
               linestyle="--", alpha=0.6, label=f"고혈당 기준 ({req.target_high:.0f})")
    ax.axhline(req.target_low, color=COLOR_BELOW, linewidth=0.8,
               linestyle="--", alpha=0.6, label=f"저혈당 기준 ({req.target_low:.0f})")

    ax.set_xticks(x)
    ax.set_xticklabels(dates, fontsize=9)
    ax.set_ylabel("혈당 (mg/dL)", fontsize=10)
    ax.set_title("주간 혈당 추이", fontsize=14, fontweight="bold", pad=15)
    ax.legend(loc="upper right", fontsize=8, framealpha=0.9)
    ax.grid(axis="y", linestyle="--", alpha=0.4)
    ax.set_ylim(
        max(0, min(mins) - 20),
        max(maxs) + 30,
    )
    ax.spines[["top", "right"]].set_visible(False)

    fig.tight_layout(rect=[0.03, 0.03, 0.97, 0.97])
    pdf.savefig(fig, bbox_inches="tight")
    plt.close(fig)


# ── Page 3: 시간대별 혈당 패턴 ──────────────────────────────────────

def _page_hourly_pattern(pdf: PdfPages, req: WeeklyReportRequest) -> None:
    if not req.hourly_avg_glucose:
        return

    fig, ax = plt.subplots(figsize=(A4_W, A4_H))
    fig.patch.set_facecolor("white")

    hour_map = {h.hour: h.avg for h in req.hourly_avg_glucose}
    hours = list(range(24))
    avgs = [hour_map.get(h, None) for h in hours]

    # 데이터 있는 시간대만 표시
    valid_hours = [h for h, v in zip(hours, avgs) if v is not None]
    valid_avgs = [v for v in avgs if v is not None]

    bar_colors = [
        COLOR_ABOVE if v > req.target_high
        else COLOR_BELOW if v < req.target_low
        else COLOR_IN_RANGE
        for v in valid_avgs
    ]

    bars = ax.bar(valid_hours, valid_avgs, color=bar_colors,
                  width=0.7, edgecolor="white", linewidth=0.5, zorder=2)

    # 목표 범위 배경
    ax.axhspan(req.target_low, req.target_high,
               alpha=0.08, color=COLOR_IN_RANGE)
    ax.axhline(req.target_high, color=COLOR_ABOVE, linewidth=0.8,
               linestyle="--", alpha=0.6)
    ax.axhline(req.target_low, color=COLOR_BELOW, linewidth=0.8,
               linestyle="--", alpha=0.6)

    # 수치 레이블 (공간 있을 때만)
    for bar, val in zip(bars, valid_avgs):
        ax.text(bar.get_x() + bar.get_width() / 2, val + 2,
                f"{val:.0f}", ha="center", va="bottom", fontsize=6.5)

    # 시간대 구간 배경
    ax.axvspan(-0.5, 5.5, alpha=0.04, color="#9E9E9E", label="야간 (0~5시)")
    ax.axvspan(5.5, 9.5, alpha=0.04, color="#FFF9C4", label="아침 (6~9시)")

    # 범례
    patches = [
        mpatches.Patch(fc=COLOR_IN_RANGE, label="목표 범위"),
        mpatches.Patch(fc=COLOR_ABOVE, label=f"고혈당 (>{req.target_high:.0f})"),
        mpatches.Patch(fc=COLOR_BELOW, label=f"저혈당 (<{req.target_low:.0f})"),
    ]
    ax.legend(handles=patches, loc="upper right", fontsize=8)

    ax.set_xticks(list(range(0, 24, 2)))
    ax.set_xticklabels([f"{h}시" for h in range(0, 24, 2)], fontsize=8)
    ax.set_xlabel("시간대", fontsize=10)
    ax.set_ylabel("평균 혈당 (mg/dL)", fontsize=10)
    ax.set_title("시간대별 혈당 패턴 (주간 평균)", fontsize=14, fontweight="bold", pad=15)
    ax.grid(axis="y", linestyle="--", alpha=0.4, zorder=0)
    ax.spines[["top", "right"]].set_visible(False)

    fig.tight_layout(rect=[0.03, 0.03, 0.97, 0.97])
    pdf.savefig(fig, bbox_inches="tight")
    plt.close(fig)


# ── Page 4: AI 요약 + 코칭 제안 ─────────────────────────────────────

def _page_ai_text(pdf: PdfPages, ai_summary: str, ai_suggest: str,
                  good_foods: list, bad_foods: list) -> None:
    fig = plt.figure(figsize=(A4_W, A4_H))
    fig.patch.set_facecolor("white")

    # 섹션 헤더 스타일 텍스트 박스
    def _section(ax_fig, y_top: float, title: str, body: str,
                 header_color: str, y_end_out: list) -> None:
        title_h = 0.06
        body_lines = _wrap_text(body, width=60).split("\n")
        line_h = 0.022
        body_h = max(len(body_lines) * line_h + 0.04, 0.12)

        # 헤더
        ax_t = fig.add_axes([0.05, y_top - title_h, 0.9, title_h])
        ax_t.set_axis_off()
        ax_t.add_patch(mpatches.FancyBboxPatch(
            (0, 0), 1, 1, boxstyle="round,pad=0", fc=header_color, ec="none",
            transform=ax_t.transAxes,
        ))
        ax_t.text(0.03, 0.5, title, va="center", fontsize=11,
                  fontweight="bold", color="white", transform=ax_t.transAxes)

        # 본문
        ax_b = fig.add_axes([0.05, y_top - title_h - body_h, 0.9, body_h])
        ax_b.set_axis_off()
        ax_b.add_patch(mpatches.FancyBboxPatch(
            (0, 0), 1, 1, boxstyle="round,pad=0", fc="#FAFAFA", ec="#E0E0E0", linewidth=0.8,
            transform=ax_b.transAxes,
        ))
        ax_b.text(0.03, 0.97, body, va="top", fontsize=9, linespacing=1.6,
                  color="#212121", transform=ax_b.transAxes, wrap=True)
        y_end_out.append(y_top - title_h - body_h - 0.02)

    # AI 요약
    y_ref: list[float] = []
    _section(fig, 0.93, "이번 주 혈당 요약", ai_summary, "#1565C0", y_ref)

    # 코칭 제안
    y_next = y_ref[-1] if y_ref else 0.55
    y_ref2: list[float] = []
    _section(fig, y_next, "다음 주 코칭 제안", ai_suggest, "#2E7D32", y_ref2)

    # GOOD/BAD 음식
    y_food = y_ref2[-1] if y_ref2 else 0.2
    if good_foods or bad_foods:
        ax_food = fig.add_axes([0.05, y_food - 0.22, 0.9, 0.18])
        ax_food.set_axis_off()
        ax_food.add_patch(mpatches.FancyBboxPatch(
            (0, 0), 1, 1, boxstyle="round,pad=0", fc="#FAFAFA", ec="#E0E0E0", linewidth=0.8,
            transform=ax_food.transAxes,
        ))
        ax_food.text(0.03, 0.92, "이번 주 식품 등급", va="top", fontsize=10,
                     fontweight="bold", color="#212121", transform=ax_food.transAxes)
        good_str = "  ".join(f.food_name for f in good_foods[:5]) or "-"
        bad_str = "  ".join(f.food_name for f in bad_foods[:5]) or "-"
        ax_food.text(0.03, 0.65, f"혈당에 좋은 음식 ✓  {good_str}",
                     va="top", fontsize=8.5, color="#2E7D32", transform=ax_food.transAxes)
        ax_food.text(0.03, 0.38, f"혈당에 주의할 음식 ✗  {bad_str}",
                     va="top", fontsize=8.5, color="#C62828", transform=ax_food.transAxes)

    fig.text(0.5, 0.02, "본 리포트는 AI가 생성한 참고 자료이며, 의료적 진단을 대체하지 않습니다.",
             ha="center", fontsize=7, color="#9E9E9E", style="italic")

    pdf.savefig(fig, bbox_inches="tight")
    plt.close(fig)


# ── 메인 진입점 ──────────────────────────────────────────────────────

def generate_pdf(req: WeeklyReportRequest, ai_summary: str, ai_suggest: str) -> bytes:
    """
    주간 보고서 PDF를 생성하고 bytes로 반환한다.

    Args:
        req: BE에서 전달한 집계 데이터
        ai_summary: LLM이 생성한 주간 요약
        ai_suggest: LLM이 생성한 코칭 제안

    Returns:
        PDF bytes
    """
    _setup_korean_font()

    buf = io.BytesIO()
    with PdfPages(buf) as pdf:
        meta = pdf.infodict()
        meta["Title"] = f"주간 혈당 관리 리포트 {req.week_start}"
        meta["Author"] = "GlucoAI"
        meta["Subject"] = f"{req.user_name} 주간 보고서"

        _page_cover(pdf, req)
        _page_weekly_trend(pdf, req)
        _page_hourly_pattern(pdf, req)
        _page_ai_text(pdf, ai_summary, ai_suggest, req.good_foods, req.bad_foods)

    return buf.getvalue()
