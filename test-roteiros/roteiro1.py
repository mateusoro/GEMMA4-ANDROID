#!/usr/bin/env python3
"""
Roteiro REST-only simplificado:
1. Para e inicia o app via adb.
2. Faz adb forward.
3. Verifica status da API.
4. Envia uma única pergunta e retorna a resposta.

Uso: python roteiro1.py --message "Sua pergunta aqui"
     python roteiro1.py --message "Liste os arquivos markdown do workspace"

Tempo de carregamento do LLM: ~5 segundos após "modelReady=true".
O sistema de ferramentas do modelo responde em ~5-10s. Timeout padrao: 60s.
"""

from __future__ import annotations

import argparse
import json
import os
import queue
import re
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
import urllib.error
import http.client
from datetime import datetime

PACKAGE = "com.gemma.gpuchat"


class ExecutionLog:
    def __init__(self):
        self.start_time = datetime.now()
        self.steps = []
        self.final_result = None

    def add_step(self, step_num, step_count, title, status, details=""):
        self.steps.append({
            "step": step_num,
            "total": step_count,
            "title": title,
            "status": status,
            "details": details,
            "timestamp": datetime.now().isoformat()
        })

    def set_final_result(self, success, response="", duration_ms=0):
        self.final_result = {
            "success": success,
            "response": response,
            "duration_ms": duration_ms,
            "end_time": datetime.now().isoformat()
        }

    def to_markdown(self):
        duration = datetime.now() - self.start_time
        lines = [
            "# Roteiro 1 Execution Log",
            "",
            f"**Start Time:** {self.start_time.isoformat()}",
            f"**Duration:** {duration}",
            ""
        ]

        for step in self.steps:
            status_emoji = "✅" if step["status"] == "success" else "❌" if step["status"] == "error" else "⏳"
            lines.append(f"### [{step['step']}/{step['total']}] {step['title']}")
            lines.append(f"- **Status:** {status_emoji} {step['status']}")
            lines.append(f"- **Timestamp:** {step['timestamp']}")
            if step["details"]:
                lines.append(f"- **Details:** {step['details']}")
            lines.append("")

        if self.final_result:
            lines.append("## Final Result")
            lines.append("")
            result_status = "✅ SUCCESS" if self.final_result["success"] else "❌ FAILED"
            lines.append(f"**Status:** {result_status}")
            if self.final_result.get("duration_ms"):
                lines.append(f"**Duration:** {self.final_result['duration_ms']}ms")
            lines.append("")
            if self.final_result.get("response"):
                lines.append("### Response")
                lines.append("")
                lines.append("```")
                lines.append(self.final_result["response"][:1000])
                lines.append("```")
                lines.append("")

        return "\n".join(lines)

    def save(self, logs_dir="logs"):
        os.makedirs(logs_dir, exist_ok=True)
        timestamp = self.start_time.strftime("%Y%m%d_%H%M%S")
        filepath = os.path.join(logs_dir, f"roteiro1_{timestamp}.md")
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(self.to_markdown())
        return filepath


def adb_command(serial: str | None, *args: str) -> list[str]:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    return cmd


def adb_forward(serial: str | None, local: str, remote: str) -> None:
    cmd = adb_command(serial, "forward", local, remote)
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
    if result.returncode != 0:
        raise RuntimeError(f"adb forward failed: {result.stderr}")


def request_with_retries(fn, retries: int = 3, delay: float = 2.0) -> dict:
    last_error: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            return fn()
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {exc.code} {exc.reason}: {body}") from exc
        except (urllib.error.URLError, http.client.RemoteDisconnected, ConnectionResetError) as exc:
            last_error = exc
            if attempt < retries:
                print(f"  retry {attempt}/{retries} after connection error: {exc}")
                time.sleep(delay)
                continue
            break
    raise RuntimeError(f"connection failed after {retries} attempts: {last_error}")


def post_form(base_url: str, path: str, data: dict[str, str], timeout: int, retries: int = 3) -> dict:
    def do_request() -> dict:
        body = urllib.parse.urlencode(data).encode("utf-8")
        req = urllib.request.Request(
            f"{base_url}{path}",
            data=body,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))

    return request_with_retries(do_request, retries=retries)


def adb_pidof(serial: str | None) -> str:
    result = subprocess.run(
        adb_command(serial, "shell", "pidof", PACKAGE),
        capture_output=True,
        text=True,
        timeout=5,
    )
    return result.stdout.strip()


def adb_stop(serial: str | None) -> None:
    result = subprocess.run(
        adb_command(serial, "shell", "am", "force-stop", PACKAGE),
        capture_output=True,
        text=True,
        timeout=10,
    )
    time.sleep(1)


def adb_start(wait_seconds: float, serial: str | None) -> None:
    result = subprocess.run(
        adb_command(serial, "shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity"),
        capture_output=True,
        text=True,
        timeout=15,
    )
    if result.returncode != 0:
        raise RuntimeError((result.stderr or result.stdout or "adb start failed").strip())
    if wait_seconds > 0:
        time.sleep(wait_seconds)


def post_form_monitored(
    base_url: str,
    path: str,
    data: dict[str, str],
    timeout: int,
    label: str,
    serial: str | None,
    hang_timeout: int,
    probe_interval: int,
) -> dict:
    result_queue: queue.Queue[tuple[str, dict | BaseException]] = queue.Queue(maxsize=1)

    def worker() -> None:
        try:
            result_queue.put(("ok", post_form(base_url, path, data, timeout=timeout, retries=1)))
        except BaseException as exc:
            result_queue.put(("error", exc))

    thread = threading.Thread(target=worker, daemon=True)
    thread.start()
    started = time.time()

    while True:
        try:
            kind, value = result_queue.get(timeout=probe_interval)
            if kind == "ok":
                return value  # type: ignore[return-value]
            raise RuntimeError(f"{label} failed: {value}") from value
        except queue.Empty:
            elapsed = int(time.time() - started)
            pid = adb_pidof(serial) if serial else "unknown"
            if serial and not pid:
                raise RuntimeError(f"{label} aborted after {elapsed}s: app process is not running")
            print(f"  waiting {label}: {elapsed}s elapsed, app_pid={pid}")
            if elapsed >= hang_timeout:
                raise RuntimeError(
                    f"{label} appears stuck after {elapsed}s "
                    f"(hang-timeout={hang_timeout}s, app_pid={pid})"
                )


def get_json(base_url: str, path: str, timeout: int, retries: int = 3) -> dict:
    def do_request() -> dict:
        with urllib.request.urlopen(f"{base_url}{path}", timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))

    return request_with_retries(do_request, retries=retries)


def wait_for_model_ready(base_url: str, timeout_seconds: int) -> dict:
    deadline = time.time() + timeout_seconds
    last_status: dict | None = None
    while time.time() < deadline:
        try:
            status = get_json(base_url, "/api/status", timeout=5, retries=1)
            last_status = status
            if status.get("modelReady") is True:
                return status
            print(f"  waiting modelReady=true; current={status}")
        except Exception as exc:
            print(f"  waiting API status; error={exc}")
        time.sleep(3)
    raise RuntimeError(f"model was not ready after {timeout_seconds}s; last_status={last_status}")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Roteiro REST-only: envia uma pergunta e retorna a resposta do modelo.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Exemplos:
  python roteiro1.py --message "Liste os arquivos markdown do workspace"
  python roteiro1.py --message "Qual e a capital do Brasil?"
  python roteiro1.py --message "Leia o arquivo markdown/teste.md com limite de 20 linhas"
        """
    )
    parser.add_argument("--adb-serial", default=None, help="Serial do dispositivo ADB")
    parser.add_argument("--timeout", type=int, default=60, help="Timeout da requisicao em segundos (default: 60)")
    parser.add_argument("--hang-timeout", type=int, default=90, help="Timeout total do roteiro em segundos (default: 90)")
    parser.add_argument("--probe-interval", type=int, default=10, help="Intervalo de verificacao em segundos (default: 10)")
    parser.add_argument("--adb-wait", type=float, default=5.0, help="Tempo de espera apos iniciar app em segundos (default: 5)")
    parser.add_argument("--skip-reload", action="store_true", default=False, help="Pula reload da conversa")
    parser.add_argument("--message", "-m", dest="message", default="Liste os arquivos markdown do workspace", help="Pergunta a enviar ao modelo")
    parser.add_argument("message_remainder", nargs=argparse.REMAINDER, help=argparse.SUPPRESS)  # captura resto como pergunta
    args = parser.parse_args()

    # Se passou texto livre (sem --), junta tudo como pergunta
    if args.message_remainder:
        args.message = " ".join(args.message_remainder)

    base_url = "http://127.0.0.1:8080"
    step = 1
    step_count = 6 if args.skip_reload else 7

    exec_log = ExecutionLog()

    try:
        # 1. Parar app
        print(f"[{step}/{step_count}] adb stop {PACKAGE}")
        adb_stop(args.adb_serial)
        exec_log.add_step(step, step_count, f"adb stop {PACKAGE}", "success")
        step += 1

        # 2. Iniciar app
        print(f"[{step}/{step_count}] adb start {PACKAGE}")
        adb_start(args.adb_wait, args.adb_serial)
        exec_log.add_step(step, step_count, f"adb start {PACKAGE}", "success")
        step += 1

        # 3. Forward
        print(f"[{step}/{step_count}] adb forward tcp:8080 tcp:8080")
        adb_forward(args.adb_serial, "tcp:8080", "tcp:8080")
        exec_log.add_step(step, step_count, "adb forward", "success")
        step += 1

        # 4. Aguardar model ready (LLM carrega em ~5s apos status=true)
        print(f"[{step}/{step_count}] wait for model ready (~5s after status)")
        status = wait_for_model_ready(base_url, timeout_seconds=90)
        print(json.dumps(status, ensure_ascii=False))
        require(status.get("modelReady") is True, "modelReady != true")
        exec_log.add_step(step, step_count, "Model ready", "success", f"modelReady={status.get('modelReady')}")
        step += 1

        # 5. Reload conversa (opcional)
        if not args.skip_reload:
            print(f"[{step}/{step_count}] reload/clear chat")
            reload_result = post_form(
                base_url,
                "/api/llm/reload",
                {"clearChat": "true", "timeoutMs": "60000"},
                timeout=90,
            )
            print(json.dumps(reload_result, ensure_ascii=False))
            require(reload_result.get("status") == "completed", "reload did not complete")
            exec_log.add_step(step, step_count, "Reload/clear chat", "success")
        else:
            print(f"[{step}/{step_count}] skip reload")
            exec_log.add_step(step, step_count, "Skip reload", "skipped")
        step += 1

        # 6. Enviar pergunta
        print(f"[{step}/{step_count}] send message")
        print(f"  message: {args.message}")
        result = post_form_monitored(
            base_url,
            "/api/message",
            {"message": args.message, "timeoutMs": str(args.timeout * 1000)},
            timeout=args.timeout,
            label="message",
            serial=args.adb_serial,
            hang_timeout=args.hang_timeout,
            probe_interval=args.probe_interval,
        )
        print(json.dumps(result, ensure_ascii=False))
        require(result.get("status") == "completed", f"message did not complete: status={result.get('status')}")
        exec_log.add_step(step, step_count, "Send message", "success", f"durationMs={result.get('durationMs')}")

        print("\nRESULT:")
        response_text = result.get("response", "").strip()
        print(response_text)

        exec_log.set_final_result(True, response_text, result.get("durationMs", 0))
        log_file = exec_log.save()
        print(f"\n✅ Execution log saved to: {log_file}")

        return 0

    except Exception as exc:
        exec_log.set_final_result(False, str(exc))
        log_file = exec_log.save()
        print(f"\n❌ Error: {exc}")
        print(f"Execution log saved to: {log_file}")
        raise


if __name__ == "__main__":
    import signal

    def timeout_handler(signum, frame):
        print("TIMEOUT: script exceeded limit", file=sys.stderr)
        raise SystemExit(1)

    signal.signal(signal.SIGALRM, timeout_handler)
    signal.alarm(120)

    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)