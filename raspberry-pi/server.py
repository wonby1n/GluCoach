#!/usr/bin/env python3
"""
GlucoFit Projection Server
Pi Zero 2W + YG300 광학 모듈 제어 서버 (포트 9999)

명령어:
  SHOW                        → 대기 영상 루프 재생
  HIDE                        → 영상 종료
  BRIEFING:<수면점수>:<혈당>   → 브리핑 영상 1회 재생
  ALERT                       → 고혈당 경고 영상 루프 재생
"""

import os
import socket
import subprocess
import threading
import logging

HOME_DIR = os.path.expanduser("~")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler(os.path.join(HOME_DIR, "projection_server.log")),
        logging.StreamHandler(),
    ],
)
log = logging.getLogger(__name__)

HOST = "0.0.0.0"
PORT = 9999

VIDEO_DIR = os.path.join(HOME_DIR, "videos")
VIDEOS = {
    "idle":     os.path.join(VIDEO_DIR, "assistant_idle.mp4"),
    "briefing": os.path.join(VIDEO_DIR, "assistant_briefing.mp4"),
    "alert":    os.path.join(VIDEO_DIR, "assistant_alert.mp4"),
}

_proc: subprocess.Popen | None = None
_lock = threading.Lock()
_env = {
    **os.environ,
    "WAYLAND_DISPLAY": "wayland-0",
    "XDG_RUNTIME_DIR": f"/run/user/{os.getuid()}",
}


def _stop():
    global _proc
    with _lock:
        if _proc and _proc.poll() is None:
            _proc.terminate()
            try:
                _proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                _proc.kill()
        _proc = None


def _play(key: str, loop: bool = True):
    path = VIDEOS.get(key)
    if not path or not os.path.exists(path):
        log.warning("영상 파일 없음: %s → %s", key, path)
        return
    _stop()
    cmd = ["mpv", "--fullscreen", "--no-osd", "--no-terminal", "--really-quiet"]
    if loop:
        cmd.append("--loop=inf")
    cmd.append(path)
    with _lock:
        global _proc
        _proc = subprocess.Popen(cmd, env=_env)
    log.info("재생 시작: %s (loop=%s)", key, loop)


def _handle(cmd: str):
    cmd = cmd.strip()
    if not cmd:
        return
    log.info("명령 수신: '%s'", cmd)

    if cmd == "SHOW":
        _play("idle", loop=True)

    elif cmd == "HIDE":
        _stop()

    elif cmd.startswith("BRIEFING:"):
        parts = cmd.split(":", 2)
        sleep_score = parts[1] if len(parts) > 1 else "N/A"
        glucose    = parts[2] if len(parts) > 2 else "N/A"
        log.info("브리핑 — 수면점수=%s, 혈당=%s", sleep_score, glucose)
        _play("briefing", loop=False)

    elif cmd == "ALERT":
        _play("alert", loop=True)

    else:
        log.warning("알 수 없는 명령: '%s'", cmd)


def _client_worker(conn: socket.socket, addr):
    log.info("연결: %s", addr)
    buf = ""
    try:
        while True:
            data = conn.recv(1024)
            if not data:
                break
            buf += data.decode("utf-8", errors="ignore")
            while "\n" in buf:
                line, buf = buf.split("\n", 1)
                _handle(line)
    except OSError as e:
        log.debug("소켓 오류 (%s): %s", addr, e)
    finally:
        conn.close()
        log.info("연결 종료: %s", addr)


def main():
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((HOST, PORT))
    srv.listen(5)
    log.info("서버 시작: %s:%d", HOST, PORT)

    while True:
        conn, addr = srv.accept()
        t = threading.Thread(target=_client_worker, args=(conn, addr), daemon=True)
        t.start()


if __name__ == "__main__":
    main()
