"""주간 보고서 PDF 생성 모듈 (WeasyPrint + Jinja2).

  1. matplotlib으로 차트 생성 → base64 PNG
  2. Jinja2 HTML 템플릿에 데이터 + 차트 주입
  3. WeasyPrint로 HTML → PDF bytes 변환

인터페이스: generate_pdf(req, ai_summary, ai_suggest) -> bytes  (변경 없음)
"""

import base64
import io
import logging
from datetime import datetime

import matplotlib
import matplotlib.font_manager as fm
import matplotlib.patches as mpatches
import matplotlib.pyplot as plt
from jinja2 import Template

from app.schemas.report import WeeklyReportRequest

log = logging.getLogger(__name__)

# ── 디자인 토큰 (HTML 템플릿 & 차트 공통) ────────────────────────────
C_NAVY    = "#1E3A5F"
C_BLUE    = "#1D4ED8"
C_GREEN   = "#16A34A"   # TIR / 정상
C_RED     = "#DC2626"   # TAR / 고혈당
C_ORANGE  = "#D97706"   # TBR / 저혈당
C_FILL    = "#BFDBFE"   # 범위 채움색


# ── 한글 폰트 (matplotlib) ────────────────────────────────────────────

def _setup_korean_font() -> None:
    candidates = [
        "Noto Sans CJK KR", "NotoSansCJK-Regular",
        "Malgun Gothic", "AppleGothic", "NanumGothic",
    ]
    available = {f.name for f in fm.fontManager.ttflist}
    for name in candidates:
        if name in available:
            matplotlib.rcParams["font.family"] = name
            return
    for f in fm.findSystemFonts(fontext="ttf"):
        if any(k in f for k in ("NotoSansCJK", "Malgun", "Nanum")):
            prop = fm.FontProperties(fname=f)
            matplotlib.rcParams["font.family"] = prop.get_name()
            return


def _fig_to_b64(fig: plt.Figure) -> str:
    buf = io.BytesIO()
    try:
        fig.savefig(buf, format="png", dpi=150, bbox_inches="tight",
                    facecolor="white", edgecolor="none")
        return base64.b64encode(buf.getvalue()).decode()
    finally:
        plt.close(fig)


# ── 차트 ─────────────────────────────────────────────────────────────

def _build_tir_chart(req: WeeklyReportRequest) -> str:
    """TIR 도넛 차트."""
    fig, ax = plt.subplots(figsize=(3.6, 3.6), subplot_kw={"aspect": "equal"})
    fig.patch.set_facecolor("white")
    sizes = [
        max(req.time_in_range, 0.01),
        max(req.time_above_range, 0.01),
        max(req.time_below_range, 0.01),
    ]
    ax.pie(
        sizes,
        colors=[C_GREEN, C_RED, C_ORANGE],
        startangle=90,
        counterclock=False,
        wedgeprops={"width": 0.46, "edgecolor": "white", "linewidth": 3},
    )
    ax.text(0, 0.1, f"{req.time_in_range:.1f}%",
            ha="center", va="center", fontsize=21, fontweight="bold", color=C_NAVY)
    ax.text(0, -0.22, "목표 범위",
            ha="center", va="center", fontsize=9, color="#94A3B8")
    return _fig_to_b64(fig)


def _build_trend_chart(req: WeeklyReportRequest) -> str:
    """주간 혈당 추이 차트."""
    if not req.daily_avg_glucose:
        return ""

    fig, ax = plt.subplots(figsize=(7.0, 4.2))
    fig.patch.set_facecolor("white")
    ax.set_facecolor("white")

    dates = [d.date[5:] for d in req.daily_avg_glucose]
    avgs  = [d.avg for d in req.daily_avg_glucose]
    mins  = [d.min for d in req.daily_avg_glucose]
    maxs  = [d.max for d in req.daily_avg_glucose]
    x = list(range(len(dates)))

    # 목표 범위 배경
    ax.axhspan(req.target_low, req.target_high,
               alpha=0.08, color=C_GREEN, zorder=0)

    # 최저~최고 범위
    ax.fill_between(x, mins, maxs,
                    alpha=0.12, color=C_FILL, label="최저~최고 범위", zorder=1)

    # 평균 선
    ax.plot(x, avgs, "-", color=C_NAVY, linewidth=2.5, zorder=4, label="일평균 혈당")
    ax.plot(x, avgs, "o", color=C_NAVY, markersize=8,
            markerfacecolor="white", markeredgewidth=2.5, zorder=5)

    # 수치 레이블
    for xi, avg in zip(x, avgs):
        ax.annotate(
            f"{avg:.0f}", (xi, avg),
            textcoords="offset points", xytext=(0, 13),
            ha="center", fontsize=8.5, color=C_NAVY, fontweight="bold",
        )

    # 기준선
    ax.axhline(req.target_high, color=C_RED,    lw=1.2, ls="--", alpha=0.65,
               label=f"고혈당 기준 ({req.target_high:.0f})")
    ax.axhline(req.target_low,  color=C_ORANGE, lw=1.2, ls="--", alpha=0.65,
               label=f"저혈당 기준 ({req.target_low:.0f})")

    ax.set_xticks(x)
    ax.set_xticklabels(dates, fontsize=9, color="#475569")
    ax.set_ylabel("혈당 (mg/dL)", fontsize=9, color="#475569")
    ax.tick_params(axis="both", colors="#475569", length=0)
    ax.legend(loc="upper right", fontsize=8, framealpha=0.95,
              edgecolor="#E2E8F0", fancybox=False)
    ax.grid(axis="y", ls="--", alpha=0.25, color="#CBD5E0", zorder=0)
    for spine in ax.spines.values():
        spine.set_visible(False)
    ax.set_ylim(max(0, min(mins) - 28), max(maxs) + 52)

    fig.tight_layout(pad=1.5)
    return _fig_to_b64(fig)


def _build_hourly_chart(req: WeeklyReportRequest) -> str:
    """시간대별 혈당 패턴 차트."""
    if not req.hourly_avg_glucose:
        return ""

    fig, ax = plt.subplots(figsize=(7.0, 4.2))
    fig.patch.set_facecolor("white")
    ax.set_facecolor("white")

    # 시간대 배경 밴드 (야간 / 식사 시간대)
    ax.axvspan(-0.5,  5.5, alpha=0.28, color="#EFF6FF", zorder=0, lw=0)  # 야간
    ax.axvspan( 6.5,  9.5, alpha=0.28, color="#FFFBEB", zorder=0, lw=0)  # 아침
    ax.axvspan(11.5, 13.5, alpha=0.28, color="#FFFBEB", zorder=0, lw=0)  # 점심
    ax.axvspan(17.5, 20.5, alpha=0.28, color="#FFFBEB", zorder=0, lw=0)  # 저녁

    hour_map    = {h.hour: h.avg for h in req.hourly_avg_glucose}
    valid_hours = sorted(hour_map)
    valid_avgs  = [hour_map[h] for h in valid_hours]

    bar_colors = [
        C_RED    if v > req.target_high else
        C_ORANGE if v < req.target_low  else
        C_GREEN
        for v in valid_avgs
    ]

    bars = ax.bar(valid_hours, valid_avgs, color=bar_colors,
                  width=0.7, edgecolor="white", linewidth=0.8,
                  zorder=2, alpha=0.87)

    ax.axhspan(req.target_low, req.target_high,
               alpha=0.06, color=C_GREEN, zorder=0)
    ax.axhline(req.target_high, color=C_RED,    lw=1.1, ls="--", alpha=0.6, zorder=1)
    ax.axhline(req.target_low,  color=C_ORANGE, lw=1.1, ls="--", alpha=0.6, zorder=1)

    for bar, val in zip(bars, valid_avgs):
        ax.text(bar.get_x() + bar.get_width() / 2, val + 2,
                f"{val:.0f}", ha="center", va="bottom",
                fontsize=6.2, color="#334155")

    patches = [
        mpatches.Patch(fc=C_GREEN,  label="목표 범위"),
        mpatches.Patch(fc=C_RED,    label=f"고혈당 (>{req.target_high:.0f})"),
        mpatches.Patch(fc=C_ORANGE, label=f"저혈당 (<{req.target_low:.0f})"),
    ]
    ax.legend(handles=patches, loc="upper right", fontsize=8,
              framealpha=0.95, edgecolor="#E2E8F0", fancybox=False)

    ax.set_xticks(range(0, 24, 2))
    ax.set_xticklabels([f"{h}시" for h in range(0, 24, 2)], fontsize=8.5, color="#475569")
    ax.set_xlabel("시간대", fontsize=9, color="#475569")
    ax.set_ylabel("평균 혈당 (mg/dL)", fontsize=9, color="#475569")
    ax.tick_params(axis="both", colors="#475569", length=0)
    ax.grid(axis="y", ls="--", alpha=0.25, color="#CBD5E0", zorder=0)
    for spine in ax.spines.values():
        spine.set_visible(False)

    # 시간대 레이블
    y_top = (max(valid_avgs) if valid_avgs else req.target_high) + 18
    for x_pos, label in [(2.5, "야간"), (8, "아침"), (12.5, "점심"), (19, "저녁")]:
        ax.text(x_pos, y_top, label, ha="center", va="bottom",
                fontsize=6.5, color="#94A3B8", style="italic")

    fig.tight_layout(pad=1.5)
    return _fig_to_b64(fig)


# ── HTML 템플릿 ───────────────────────────────────────────────────────

_REPORT_HTML = """<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<style>
@page { size: A4; margin: 0; }
* { margin: 0; padding: 0; box-sizing: border-box; }

body {
    font-family: 'Noto Sans CJK KR', 'Malgun Gothic', 'Apple SD Gothic Neo', sans-serif;
    font-size: 10pt;
    color: #1E293B;
    background: white;
}

.page {
    width: 210mm;
    background: white;
    page-break-after: always;
    overflow: hidden;
}
.page:last-child { page-break-after: avoid; }


/* ══ HEADER ══════════════════════════════════════════════════════════ */
.header {
    background: linear-gradient(135deg, #0F2552 0%, #1E3A8A 55%, #1D4ED8 100%);
    padding: 26px 30px 22px;
    color: white;
}
.header-eyebrow {
    font-size: 7.5pt;
    font-weight: 600;
    color: rgba(255,255,255,0.55);
    text-transform: uppercase;
    letter-spacing: 1.2px;
    margin-bottom: 8px;
}
.header-title {
    font-size: 21pt;
    font-weight: 700;
    color: white;
    letter-spacing: -0.4px;
    line-height: 1.15;
    margin-bottom: 14px;
}
.header-title span { color: #93C5FD; }
.header-meta {
    display: flex;
    gap: 0;
    border-top: 1px solid rgba(255,255,255,0.15);
    padding-top: 12px;
}
.header-meta-item {
    padding-right: 20px;
    margin-right: 20px;
    border-right: 1px solid rgba(255,255,255,0.2);
}
.header-meta-item:last-child { border-right: none; }
.header-meta-label {
    font-size: 6.5pt;
    color: rgba(255,255,255,0.5);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 3px;
}
.header-meta-value {
    font-size: 9.5pt;
    font-weight: 600;
    color: white;
}


/* ══ TIR STRIP ════════════════════════════════════════════════════════ */
.tir-strip-section {
    padding: 14px 30px 12px;
    border-bottom: 1px solid #F1F5F9;
}
.tir-strip-title {
    font-size: 7.5pt;
    font-weight: 600;
    color: #94A3B8;
    text-transform: uppercase;
    letter-spacing: 0.6px;
    margin-bottom: 7px;
}
.tir-strip {
    display: flex;
    height: 20px;
    border-radius: 6px;
    overflow: hidden;
    gap: 2px;
}
.tir-strip-seg {
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 7.5pt;
    font-weight: 700;
    color: rgba(255,255,255,0.95);
}
.tir-strip-legend {
    display: flex;
    gap: 18px;
    margin-top: 7px;
}
.tir-legend-item {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 7.5pt;
    color: #64748B;
}
.tir-legend-dot {
    width: 8px;
    height: 8px;
    border-radius: 2px;
    flex-shrink: 0;
}


/* ══ METRIC CARDS ════════════════════════════════════════════════════ */
.metrics-section { padding: 14px 30px 10px; }
.metrics-row {
    display: flex;
    gap: 10px;
    margin-bottom: 10px;
}
.metrics-row:last-child { margin-bottom: 0; }
.metric-card {
    flex: 1;
    background: white;
    border: 1px solid #E2E8F0;
    border-top: 3px solid #2563EB;
    border-radius: 10px;
    padding: 12px 10px 10px;
    text-align: center;
    box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.metric-label {
    font-size: 7pt;
    font-weight: 600;
    color: #94A3B8;
    text-transform: uppercase;
    letter-spacing: 0.4px;
    margin-bottom: 7px;
}
.metric-value {
    font-size: 17pt;
    font-weight: 700;
    color: #1E3A5F;
    line-height: 1;
    margin-bottom: 3px;
}
.metric-value.sm { font-size: 12pt; }
.metric-unit { font-size: 6.5pt; color: #CBD5E1; font-weight: 500; }


/* ══ TIR + LIFESTYLE ═════════════════════════════════════════════════ */
.lower-section {
    display: flex;
    gap: 16px;
    padding: 10px 30px 14px;
}
.tir-donut-col { flex: 0 0 152px; text-align: center; }
.tir-donut-col img { width: 100%; }
.tir-donut-caption {
    font-size: 7pt;
    color: #94A3B8;
    margin-top: -6px;
}
.tir-detail-col { flex: 1; padding-top: 2px; }
.tir-detail-title {
    font-size: 7.5pt;
    font-weight: 700;
    color: #64748B;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 10px;
}
.tir-bar-group { margin-bottom: 10px; }
.tir-bar-row {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin-bottom: 4px;
}
.tir-bar-name { font-size: 8pt; color: #475569; font-weight: 500; }
.tir-bar-pct  { font-size: 9.5pt; font-weight: 700; }
.tir-bar-hint { font-size: 6.5pt; color: #94A3B8; margin-top: 3px; }
.tir-track {
    width: 100%;
    height: 8px;
    background: #F1F5F9;
    border-radius: 4px;
    overflow: hidden;
}
.tir-fill { height: 8px; border-radius: 4px; }

.lifestyle {
    display: flex;
    gap: 7px;
    margin-top: 12px;
    padding-top: 10px;
    border-top: 1px solid #F1F5F9;
}
.lifestyle-card {
    flex: 1;
    background: #F8FAFC;
    border-radius: 8px;
    padding: 8px 6px;
    text-align: center;
}
.lifestyle-label { font-size: 6pt; color: #94A3B8; margin-bottom: 4px; }
.lifestyle-val   { font-size: 9pt; font-weight: 700; color: #334155; }

.page-footer {
    padding: 4px 30px 7px;
    text-align: right;
    font-size: 6.5pt;
    color: #CBD5E1;
}


/* ══ CHART PAGES ═════════════════════════════════════════════════════ */
.chart-header {
    background: linear-gradient(90deg, #0F2552 0%, #1D4ED8 100%);
    padding: 18px 30px;
}
.chart-header-title { font-size: 15pt; font-weight: 700; color: white; }
.chart-header-sub {
    font-size: 8.5pt;
    color: rgba(255,255,255,0.7);
    margin-top: 4px;
}
.chart-accent {
    height: 3px;
    background: linear-gradient(90deg, #60A5FA, #93C5FD, transparent);
}
.chart-body { padding: 18px 24px; }
.chart-body img { width: 100%; border-radius: 6px; }
.chart-caption {
    margin: 14px 24px 0;
    padding: 10px 14px;
    background: #F8FAFC;
    border-left: 3px solid #BFDBFE;
    border-radius: 0 6px 6px 0;
    font-size: 7.5pt;
    color: #64748B;
    line-height: 1.7;
}


/* ══ AI PAGE ══════════════════════════════════════════════════════════ */
.ai-page { padding: 24px 28px; }
.ai-section { margin-bottom: 18px; }
.ai-section-header {
    border-radius: 8px 8px 0 0;
    padding: 12px 18px;
    color: white;
}
.ai-section-title { font-size: 11pt; font-weight: 700; }
.ai-section-body {
    background: #FAFBFC;
    border: 1px solid #E2E8F0;
    border-top: none;
    border-radius: 0 0 8px 8px;
    padding: 15px 18px;
    font-size: 9.5pt;
    line-height: 1.85;
    color: #334155;
    white-space: pre-wrap;
}

.food-title {
    font-size: 7.5pt;
    font-weight: 700;
    color: #64748B;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 10px;
}
.food-grid { display: flex; gap: 12px; }
.food-card {
    flex: 1;
    border-radius: 10px;
    padding: 13px 14px;
}
.food-card-good { background: #F0FDF4; border: 1px solid #BBF7D0; }
.food-card-bad  { background: #FFF7ED; border: 1px solid #FED7AA; }
.food-card-label { font-size: 8pt; font-weight: 700; margin-bottom: 9px; }
.food-tag {
    display: inline-block;
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 8pt;
    font-weight: 500;
    margin: 2px 2px;
}
.food-tag-good { background: #16A34A; color: white; }
.food-tag-bad  { background: #EA580C; color: white; }

.disclaimer {
    margin-top: 20px;
    padding: 10px 14px;
    background: #F8FAFC;
    border: 1px solid #E2E8F0;
    border-radius: 8px;
    text-align: center;
    font-size: 7.5pt;
    color: #94A3B8;
    font-style: italic;
}
</style>
</head>
<body>

<!-- ════════════════════ PAGE 1 ════════════════════ -->
<div class="page">

  <div class="header">
    <div class="header-eyebrow">주간 혈당 관리 리포트</div>
    <div class="header-title"><span>{{ user_name }}</span>님의 혈당 리포트</div>
    <div class="header-meta">
      <div class="header-meta-item">
        <div class="header-meta-label">측정 기간</div>
        <div class="header-meta-value">{{ week_start }} ~ {{ week_end }}</div>
      </div>
      <div class="header-meta-item">
        <div class="header-meta-label">당뇨 유형</div>
        <div class="header-meta-value">{{ diabetes_type }}형</div>
      </div>
      <div class="header-meta-item">
        <div class="header-meta-label">목표 혈당</div>
        <div class="header-meta-value">{{ target_low }}~{{ target_high }} mg/dL</div>
      </div>
    </div>
  </div>

  <!-- TIR Strip -->
  <div class="tir-strip-section">
    <div class="tir-strip-title">혈당 범위 분포 — 이번 주 전체</div>
    <div class="tir-strip">
      <div class="tir-strip-seg"
           style="flex:{{ tir_flex }}; background:#16A34A;">
        {% if tir_label %}{{ tir }}%{% endif %}
      </div>
      <div class="tir-strip-seg"
           style="flex:{{ tar_flex }}; background:#DC2626;">
        {% if tar_label %}{{ tar }}%{% endif %}
      </div>
      <div class="tir-strip-seg"
           style="flex:{{ tbr_flex }}; background:#D97706;">
        {% if tbr_label %}{{ tbr }}%{% endif %}
      </div>
    </div>
    <div class="tir-strip-legend">
      <div class="tir-legend-item">
        <div class="tir-legend-dot" style="background:#16A34A;"></div>
        목표 범위 {{ tir }}% &nbsp;(권장 70% 이상)
      </div>
      <div class="tir-legend-item">
        <div class="tir-legend-dot" style="background:#DC2626;"></div>
        고혈당 {{ tar }}% &nbsp;(권장 25% 미만)
      </div>
      <div class="tir-legend-item">
        <div class="tir-legend-dot" style="background:#D97706;"></div>
        저혈당 {{ tbr }}% &nbsp;(권장 4% 미만)
      </div>
    </div>
  </div>

  <!-- Metric Cards -->
  <div class="metrics-section">
    <div class="metrics-row">
      <div class="metric-card">
        <div class="metric-label">평균 혈당</div>
        <div class="metric-value">{{ avg_glucose }}</div>
        <div class="metric-unit">mg/dL</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">최저 / 최고</div>
        <div class="metric-value sm">{{ min_glucose }} / {{ max_glucose }}</div>
        <div class="metric-unit">mg/dL</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">표준편차 (SD)</div>
        <div class="metric-value">{{ glucose_sd }}</div>
        <div class="metric-unit">mg/dL</div>
      </div>
    </div>
    <div class="metrics-row">
      <div class="metric-card">
        <div class="metric-label">GMI</div>
        <div class="metric-value">{{ gmi }}</div>
        <div class="metric-unit">% (예상 HbA1c)</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">CV% (변동계수)</div>
        <div class="metric-value">{{ cv }}</div>
        <div class="metric-unit">%</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">이번 주 식사</div>
        <div class="metric-value">{{ meal_count }}</div>
        <div class="metric-unit">회</div>
      </div>
    </div>
  </div>

  <!-- TIR Donut + Bars + Lifestyle -->
  <div class="lower-section">
    <div class="tir-donut-col">
      <img src="data:image/png;base64,{{ tir_chart }}">
      <div class="tir-donut-caption">혈당 분포 (TIR)</div>
    </div>
    <div class="tir-detail-col">
      <div class="tir-detail-title">범위별 상세</div>

      <div class="tir-bar-group">
        <div class="tir-bar-row">
          <span class="tir-bar-name">목표 범위 내 (TIR)</span>
          <span class="tir-bar-pct" style="color:#16A34A;">{{ tir }}%</span>
        </div>
        <div class="tir-track">
          <div class="tir-fill"
               style="width:{{ tir_w }}%; background:linear-gradient(90deg,#4ADE80,#16A34A);"></div>
        </div>
        <div class="tir-bar-hint">권장 70% 이상</div>
      </div>

      <div class="tir-bar-group">
        <div class="tir-bar-row">
          <span class="tir-bar-name">고혈당 (TAR)</span>
          <span class="tir-bar-pct" style="color:#DC2626;">{{ tar }}%</span>
        </div>
        <div class="tir-track">
          <div class="tir-fill"
               style="width:{{ tar_w }}%; background:linear-gradient(90deg,#F87171,#DC2626);"></div>
        </div>
        <div class="tir-bar-hint">권장 25% 미만</div>
      </div>

      <div class="tir-bar-group">
        <div class="tir-bar-row">
          <span class="tir-bar-name">저혈당 (TBR)</span>
          <span class="tir-bar-pct" style="color:#D97706;">{{ tbr }}%</span>
        </div>
        <div class="tir-track">
          <div class="tir-fill"
               style="width:{{ tbr_w }}%; background:linear-gradient(90deg,#FBBF24,#D97706);"></div>
        </div>
        <div class="tir-bar-hint">권장 4% 미만</div>
      </div>

      {% if steps or sleep_total or medication %}
      <div class="lifestyle">
        {% if steps %}
        <div class="lifestyle-card">
          <div class="lifestyle-label">평균 걸음수</div>
          <div class="lifestyle-val">{{ steps }}보</div>
        </div>
        {% endif %}
        {% if sleep_total %}
        <div class="lifestyle-card">
          <div class="lifestyle-label">평균 수면</div>
          <div class="lifestyle-val">{{ sleep_h }}h {{ sleep_m }}m</div>
        </div>
        {% endif %}
        {% if medication %}
        <div class="lifestyle-card">
          <div class="lifestyle-label">복약 횟수</div>
          <div class="lifestyle-val">{{ medication }}회</div>
        </div>
        {% endif %}
      </div>
      {% endif %}
    </div>
  </div>

  <div class="page-footer">생성: {{ generated_at }}</div>
</div>


<!-- ════════════════════ PAGE 2: WEEKLY TREND ════════════════════ -->
<div class="page">
  <div class="chart-header">
    <div class="chart-header-title">주간 혈당 추이</div>
    <div class="chart-header-sub">{{ week_start }} ~ {{ week_end }} &nbsp;|&nbsp; 일별 평균 · 최저 · 최고 혈당</div>
  </div>
  <div class="chart-accent"></div>
  <div class="chart-body">
    <img src="data:image/png;base64,{{ trend_chart }}">
  </div>
  <div class="chart-caption">
    녹색 음영은 목표 혈당 범위({{ target_low }}~{{ target_high }} mg/dL)입니다.
    진한 파란 실선은 일평균 혈당이며, 하늘색 영역은 해당 일의 최저~최고 범위입니다.
    주황/빨간 점선은 각각 저혈당·고혈당 기준선입니다.
  </div>
</div>


<!-- ════════════════════ PAGE 3: HOURLY PATTERN ════════════════════ -->
<div class="page">
  <div class="chart-header">
    <div class="chart-header-title">시간대별 혈당 패턴</div>
    <div class="chart-header-sub">이번 주 시간대별 평균 혈당 (0~23시)</div>
  </div>
  <div class="chart-accent"></div>
  <div class="chart-body">
    <img src="data:image/png;base64,{{ hourly_chart }}">
  </div>
  <div class="chart-caption">
    초록색은 목표 범위 내, 빨간색은 고혈당({{ target_high }} mg/dL 초과),
    주황색은 저혈당({{ target_low }} mg/dL 미만) 시간대입니다.
    음영 배경은 야간(0~5시)·아침·점심·저녁 식사 시간대를 나타냅니다.
  </div>
</div>


<!-- ════════════════════ PAGE 4: AI ANALYSIS ════════════════════ -->
<div class="page ai-page">

  <div class="ai-section">
    <div class="ai-section-header"
         style="background:linear-gradient(90deg,#0F2552,#1D4ED8);">
      <div class="ai-section-title">이번 주 혈당 요약</div>
    </div>
    <div class="ai-section-body">{{ ai_summary }}</div>
  </div>

  <div class="ai-section">
    <div class="ai-section-header"
         style="background:linear-gradient(90deg,#064E3B,#059669);">
      <div class="ai-section-title">다음 주 코칭 제안</div>
    </div>
    <div class="ai-section-body">{{ ai_suggest }}</div>
  </div>

  {% if good_foods or bad_foods %}
  <div>
    <div class="food-title">이번 주 식품 혈당 반응</div>
    <div class="food-grid">
      {% if good_foods %}
      <div class="food-card food-card-good">
        <div class="food-card-label" style="color:#15803D;">혈당에 좋은 음식</div>
        {% for food in good_foods %}
        <span class="food-tag food-tag-good">{{ food.food_name }}</span>
        {% endfor %}
      </div>
      {% endif %}
      {% if bad_foods %}
      <div class="food-card food-card-bad">
        <div class="food-card-label" style="color:#C2410C;">혈당에 주의할 음식</div>
        {% for food in bad_foods %}
        <span class="food-tag food-tag-bad">{{ food.food_name }}</span>
        {% endfor %}
      </div>
      {% endif %}
    </div>
  </div>
  {% endif %}

  <div class="disclaimer">
    본 리포트는 AI가 생성한 참고 자료이며, 의료적 진단을 대체하지 않습니다.
  </div>
</div>

</body>
</html>"""


# ── 메인 진입점 ───────────────────────────────────────────────────────

def generate_pdf(req: WeeklyReportRequest, ai_summary: str, ai_suggest: str) -> bytes:
    """주간 보고서 PDF를 생성하고 bytes로 반환한다."""
    _setup_korean_font()

    tir_chart    = _build_tir_chart(req)
    trend_chart  = _build_trend_chart(req)
    hourly_chart = _build_hourly_chart(req)

    gmi         = round(3.31 + 0.02392 * req.avg_glucose, 1)
    cv          = round(req.glucose_sd / req.avg_glucose * 100, 1)
    sleep_total = int(req.weekly_avg_sleep_minutes or 0)

    # TIR 스트립: flex 비율, 라벨 표시 여부(8% 미만은 공간 부족)
    tir_v = req.time_in_range
    tar_v = req.time_above_range
    tbr_v = req.time_below_range

    ctx = {
        "user_name":     req.user_name,
        "week_start":    req.week_start,
        "week_end":      req.week_end,
        "diabetes_type": req.diabetes_type,
        "target_low":    f"{req.target_low:.0f}",
        "target_high":   f"{req.target_high:.0f}",
        "avg_glucose":   f"{req.avg_glucose:.1f}",
        "min_glucose":   f"{req.min_glucose:.0f}",
        "max_glucose":   f"{req.max_glucose:.0f}",
        "glucose_sd":    f"{req.glucose_sd:.1f}",
        "gmi":           f"{gmi:.1f}",
        "cv":            f"{cv:.1f}",
        "meal_count":    req.meal_count,
        # TIR strip
        "tir":           f"{tir_v:.1f}",
        "tar":           f"{tar_v:.1f}",
        "tbr":           f"{tbr_v:.1f}",
        "tir_flex":      f"{tir_v:.1f}",
        "tar_flex":      f"{tar_v:.1f}",
        "tbr_flex":      f"{max(tbr_v, 0.01):.2f}",
        "tir_label":     tir_v >= 10,
        "tar_label":     tar_v >= 10,
        "tbr_label":     tbr_v >= 10,
        # TIR progress bars
        "tir_w":         f"{min(tir_v, 100):.1f}",
        "tar_w":         f"{min(tar_v, 100):.1f}",
        "tbr_w":         f"{min(tbr_v, 100):.1f}",
        # Lifestyle
        "steps":         f"{req.weekly_avg_steps:,.0f}" if req.weekly_avg_steps else None,
        "sleep_total":   sleep_total,
        "sleep_h":       sleep_total // 60,
        "sleep_m":       sleep_total % 60,
        "medication":    req.medication_count,
        # Foods
        "good_foods":    req.good_foods[:5],
        "bad_foods":     req.bad_foods[:5],
        # AI
        "ai_summary":    ai_summary,
        "ai_suggest":    ai_suggest,
        # Charts
        "tir_chart":     tir_chart,
        "trend_chart":   trend_chart,
        "hourly_chart":  hourly_chart,
        "generated_at":  datetime.now().strftime("%Y-%m-%d %H:%M"),
    }

    html_str = Template(_REPORT_HTML).render(**ctx)
    from weasyprint import HTML  # GTK 라이브러리 필요 — 실제 PDF 생성 시에만 로드
    return HTML(string=html_str).write_pdf()
