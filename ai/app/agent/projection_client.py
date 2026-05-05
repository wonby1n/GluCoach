"""
GlucoFit 프로젝션 서버 TCP 클라이언트.
AI agent에서 라즈베리파이 소켓 서버(port 9999)로 명령을 전송한다.
"""

import os
import socket

PI_HOST = os.getenv("PROJECTION_PI_HOST", "192.168.0.100")
PI_PORT = int(os.getenv("PROJECTION_PI_PORT", "9999"))
TIMEOUT = 3


def send_command(command: str) -> dict:
    """TCP 소켓으로 프로젝션 명령을 전송한다."""
    try:
        with socket.create_connection((PI_HOST, PI_PORT), timeout=TIMEOUT) as sock:
            sock.sendall((command + "\n").encode("utf-8"))
        return {"status": "ok", "command": command}
    except OSError as e:
        return {"status": "error", "message": str(e), "command": command}
