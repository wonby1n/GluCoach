// ============================================================
//  GlucoFit — 워치형 통합 케이스
//  Pi Zero 2W + 보조배터리 일체형 / 뚜껑 스냅핏 + 손목 러그
//
//  출력 방법:
//    1. OpenSCAD에서 이 파일 열기
//    2. 아래 PRINT_PART 값 바꿔서 각각 STL 내보내기
//       PRINT_PART = 0  →  전체 미리보기 (조립 확인용)
//       PRINT_PART = 1  →  바텀 케이스만 출력
//       PRINT_PART = 2  →  뚜껑(탑 리드)만 출력
//    3. F6 Render → File > Export as STL
//    4. Cura: PLA, 레이어 0.2mm, 인필 25%, 서포트 없음
// ============================================================

/* [출력 파트 선택] */
PRINT_PART = 0;   // 0=전체보기  1=바텀  2=탑 리드

/* [보조배터리 실측값 (mm)] */
// 샤오미 10000 Pro 기준 — 구매 후 실측 필수
PB_W = 73.0;   // 가로
PB_D = 62.0;   // 세로
PB_H = 14.5;   // 두께

/* [Pi Zero 2W] */
PI_W = 65.0;
PI_D = 30.0;
PI_H = 6.0;    // PCB + 부품 여유

/* [케이스 설정] */
WALL      = 2.5;   // 벽 두께
GAP       = 0.4;   // 조립 공차
R         = 5.0;   // 모서리 곡률 (워치 느낌)
LUG_W     = 22.0;  // 러그 폭 (표준 손목 밴드 22mm)
LUG_D     = 8.0;   // 러그 길이
LUG_H     = 6.0;   // 러그 두께
LUG_PIN_R = 1.5;   // 러그 핀 구멍 반지름 (스프링바 대응)
CLIP_H    = 4.0;   // 스냅핏 클립 높이

// ============================================================
//  내부 계산
// ============================================================
INNER_W = max(PB_W, PI_W) + GAP * 2;
INNER_D = PB_D + GAP * 2 + PI_D + GAP * 2 + WALL;  // 배터리 + Pi 나란히
INNER_H_BOT = PB_H + GAP;          // 바텀 내부 깊이 (배터리 층)
INNER_H_TOP = PI_H + GAP;          // 탑 내부 깊이 (Pi 층)

OUTER_W = INNER_W + WALL * 2;
OUTER_D = INNER_D + WALL * 2;
BOT_H   = INNER_H_BOT + WALL;      // 바텀 케이스 전체 높이
TOP_H   = INNER_H_TOP + WALL + CLIP_H; // 탑 리드 전체 높이

// ============================================================
//  어셈블리 출력
// ============================================================
if (PRINT_PART == 0) {
    // 조립 미리보기
    color("SteelBlue")   bottom_case();
    color("LightSteelBlue", 0.7)
    translate([0, 0, BOT_H])
    top_lid();
}
else if (PRINT_PART == 1) {
    bottom_case();
}
else if (PRINT_PART == 2) {
    // 뒤집어서 출력 (평평한 면이 베드에 닿게)
    translate([0, OUTER_D, TOP_H])
    rotate([180, 0, 0])
    top_lid();
}

// ============================================================
//  바텀 케이스 (배터리 + 케이블 구멍 + 러그)
// ============================================================
module bottom_case() {
    difference() {
        union() {
            // 케이스 바디
            rounded_box(OUTER_W, OUTER_D, BOT_H, R);

            // 손목 러그 — 상하 4개
            for (side = [-1, 1])
            for (end  = [0, 1]) {
                lug_y = end == 0 ? -LUG_D : OUTER_D;
                translate([OUTER_W / 2 - LUG_W / 2, lug_y, 0])
                lug(side);
            }
        }

        // 내부 공간 (배터리 슬롯)
        translate([WALL, WALL, WALL])
        cube([INNER_W, PB_D + GAP * 2, INNER_H_BOT + 1]);

        // Pi 공간 (배터리 옆)
        translate([WALL, WALL + PB_D + GAP * 2 + WALL, WALL])
        cube([INNER_W, PI_D + GAP * 2, INNER_H_BOT + 1]);

        // micro-USB 전원 구멍 (배터리 측면 왼쪽)
        translate([-1, WALL + 10, WALL + 4])
        rotate([0, 90, 0])
        rounded_hole(12, 6, WALL + 2);

        // USB-A 출력 구멍 (배터리 측면 오른쪽)
        translate([OUTER_W - WALL - 1, WALL + 10, WALL + 4])
        rotate([0, 90, 0])
        rounded_hole(14, 7, WALL + 2);

        // mini-HDMI 구멍 (Pi 쪽 측면)
        translate([-1, OUTER_D - WALL - 20, WALL + 3])
        rotate([0, 90, 0])
        rounded_hole(16, 8, WALL + 2);

        // 스냅핏 홈 (상단 테두리 안쪽)
        translate([WALL + 1, WALL + 1, BOT_H - CLIP_H])
        cube([INNER_W - 2, INNER_D, CLIP_H + 1]);

        // 러그 핀 구멍
        for (side = [-1, 1])
        for (end  = [0, 1]) {
            lug_y = end == 0
                ? -LUG_D + LUG_D / 2
                : OUTER_D + LUG_D / 2;
            for (x_pos = [OUTER_W / 2 - LUG_W / 2 + LUG_PIN_R + 1,
                           OUTER_W / 2 + LUG_W / 2 - LUG_PIN_R - 1])
            translate([x_pos, lug_y, LUG_H / 2])
            rotate([90, 0, 0])
            cylinder(h = LUG_D + 2, r = LUG_PIN_R, $fn = 16, center = true);
        }
    }

    // 스냅핏 돌기 (바텀 상단 모서리 4군데)
    for (x = [WALL + 3, OUTER_W - WALL - 3 - 6])
    for (y = [WALL + 3, OUTER_D - WALL - 3 - 6])
    translate([x, y, BOT_H - CLIP_H])
    snap_peg();
}

// ============================================================
//  탑 리드 (Pi 덮개 + 스냅핏 클립)
// ============================================================
module top_lid() {
    difference() {
        union() {
            // 리드 바디
            rounded_box(OUTER_W, OUTER_D, TOP_H, R);

            // 내부 스냅핏 클립 (바텀 돌기에 끼워짐)
            for (x = [WALL + 3, OUTER_W - WALL - 3 - 6])
            for (y = [WALL + 3, OUTER_D - WALL - 3 - 6])
            translate([x, y, TOP_H - CLIP_H - WALL - PI_H - GAP])
            snap_clip_socket();
        }

        // 내부 공간 (Pi 수납)
        translate([WALL, WALL, WALL])
        cube([INNER_W, INNER_D, INNER_H_TOP + 0.5]);

        // 투명창 자리 (워치 페이스 느낌 — 글라스 끼우거나 오픈)
        translate([OUTER_W / 2, OUTER_D / 2, TOP_H - WALL - 0.5])
        scale([1, 1, 1])
        hull() {
            for (dx = [-OUTER_W / 2 + WALL * 2 + R,
                        OUTER_W / 2 - WALL * 2 - R])
            for (dy = [-OUTER_D / 2 + WALL * 2 + R,
                        OUTER_D / 2 - WALL * 2 - R])
            translate([dx, dy, 0])
            cylinder(h = WALL + 1, r = R, $fn = 32);
        }

        // 전원 버튼 구멍 (우측 상단)
        translate([OUTER_W - WALL - 1, OUTER_D * 0.7, TOP_H / 2])
        rotate([0, 90, 0])
        cylinder(h = WALL + 2, r = 4, $fn = 32);
    }
}

// ============================================================
//  헬퍼 모듈
// ============================================================

// 라운드 박스
module rounded_box(w, d, h, r) {
    hull()
    for (x = [r, w - r])
    for (y = [r, d - r])
    translate([x, y, 0])
    cylinder(h = h, r = r, $fn = 40);
}

// 라운드 구멍 (케이블 포트)
module rounded_hole(w, h, depth) {
    r = min(w, h) / 2 * 0.6;
    hull()
    for (x = [r, w - r])
    for (z = [r, h - r])
    translate([x, 0, z])
    rotate([90, 0, 0])
    cylinder(h = depth, r = r, $fn = 16);
}

// 손목 러그
module lug(side) {
    difference() {
        hull() {
            cube([LUG_W, LUG_D, LUG_H]);
            translate([LUG_W / 2 - R, LUG_D / 2, 0])
            cylinder(h = LUG_H, r = R, $fn = 32);
        }
        // 경량화 컷
        translate([LUG_W / 2 - 4, -1, LUG_H / 2])
        rotate([-90, 0, 0])
        cylinder(h = LUG_D + 2, r = 3, $fn = 16);
    }
}

// 스냅핏 돌기 (바텀)
module snap_peg() {
    w = 6; d = 4; h = CLIP_H;
    difference() {
        cube([w, d, h]);
        // 빗면 — 뚜껑 누를 때 탄성으로 밀림
        translate([0, d, h - 2])
        rotate([45, 0, 0])
        cube([w, 3, 3]);
    }
}

// 스냅핏 소켓 (탑 리드)
module snap_clip_socket() {
    w = 6 + GAP * 2;
    d = 4 + GAP * 2;
    h = CLIP_H + 1;
    // 바깥 벽
    difference() {
        translate([-GAP, -GAP, 0])
        cube([w + WALL, d + WALL, h]);
        // 소켓 내부
        cube([w, d, h + 1]);
    }
}
