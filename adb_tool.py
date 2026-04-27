"""
ADB Gemini Tool - Helper para comandos ADB do Gemma4 Android
用法: python adb_tool.py <comando> [args]

Comandos:
    start                      - Inicia o app (am start)
    stop                       - Force stop no app
    logcat [filtro] [--save]   - Mostra logs (opcionalmente salva em arquivo)
    install                    - Instala APK debug
    push-model <caminho>       - Copia modelo .litertlm para o dispositivo
    devices                    - Lista dispositivos conectados
    shell <comando>            - Executa comando shell arbitrário
    clear                      - Limpa logcat
"""

import subprocess
import sys
import time
import os
import re
from pathlib import Path

# ============ CONFIGURAÇÃO ============
PACKAGE = "com.gemma.gpuchat"
DEVICE = "192.168.0.17:45873"
DEFAULT_MODEL_NAME = "gemma3-1b-it-q4.litertlm"
LOG_FILE = "adb_logs.txt"
# =====================================

def run(cmd, capture=True, timeout=30):
    """Executa comando shell via ADB."""
    if isinstance(cmd, str):
        cmd = f"adb -s {DEVICE} {cmd}" if cmd.startswith("shell") or cmd.startswith("logcat") or cmd.startswith("install") or cmd.startswith("push") or cmd.startswith("pull") or cmd.startswith("su ") else f"adb -s {DEVICE} {cmd}"
    else:
        cmd = [c if c != "adb" else f"adb" for c in cmd]

    full_cmd = f"adb -s {DEVICE} {cmd}" if isinstance(cmd, str) and not cmd.startswith("adb") else cmd

    try:
        result = subprocess.run(
            full_cmd if isinstance(full_cmd, str) else full_cmd,
            shell=isinstance(full_cmd, str),
            capture_output=capture,
            text=True,
            timeout=timeout
        )
        return result.stdout.strip(), result.stderr.strip(), result.returncode
    except subprocess.TimeoutExpired:
        return "", "TIMEOUT", -1
    except Exception as e:
        return "", str(e), -1

def cmd_stop():
    """Force stop no app."""
    print(f"[STOP] Force stopping {PACKAGE}...")
    out, err, code = run(f"shell am force-stop {PACKAGE}")
    if code == 0:
        print(f"[OK] App encerrado")
    else:
        print(f"[ERRO] {err}")
    return code == 0

def cmd_start():
    """Inicia o app."""
    print(f"[START] Iniciando {PACKAGE}...")
    out, err, code = run(f"shell am start -n {PACKAGE}/.MainActivity")
    if code == 0:
        print(f"[OK] App iniciado")
        if out:
            print(f"   {out}")
    else:
        print(f"[ERRO] {err}")
    return code == 0

def cmd_logcat(filter_pat=None, save=False, lines=200):
    """Mostra logs do logcat."""
    print(f"[LOGCAT] Coletando logs...")
    _, err, code = run("logcat -c")
    time.sleep(1)

    print(f"[LOGCAT] Aguardando 5s para logs aparecerem...")
    time.sleep(5)

    cmd_filter = f"logcat -d -t {lines}"
    out, err, code = run(cmd_filter, timeout=20)

    if not out and not err:
        print("[LOGCAT] Nenhum log capturado. Tentando sem -t...")
        out, err, code = run("logcat -d", timeout=20)

    if save and out:
        filepath = LOG_FILE
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(out)
        print(f"[SALVO] Logs em {filepath} ({len(out)} chars)")

    if out:
        lines_out = out.split("\n")
        print(f"\n[LOGCAT] {len(lines_out)} linhas capturadas")

        if filter_pat:
            print(f"[FILTRO] Aplicando filtro: '{filter_pat}'")
            regex = re.compile(filter_pat, re.IGNORECASE)
            filtered = [l for l in lines_out if regex.search(l)]
            print(f"[FILTRO] {len(filtered)} linhas匹配")
            for l in filtered[-50:]:
                print(l)
            return filtered
        else:
            # Mostra últimas 50 linhas
            for l in lines_out[-50:]:
                print(l)
            return lines_out
    else:
        print(f"[LOGCAT] Nenhum log. err={err}")
        return []

def cmd_install():
    """Instala APK debug."""
    apk = "app/build/outputs/apk/debug/app-debug.apk"
    if not Path(apk).exists():
        print(f"[ERRO] APK não encontrado: {apk}")
        return False
    print(f"[INSTALL] Instalando {apk}...")
    out, err, code = run(f"install -r {apk}", timeout=120)
    if code == 0 and "Success" in out:
        print(f"[OK] {out}")
        return True
    else:
        print(f"[ERRO] {err or out}")
        return False

def cmd_push_model(local_path=None):
    """Copia modelo .litertlm para o dispositivo."""
    if local_path is None:
        # Procura no diretório atual
        candidates = list(Path(".").glob("*.litertlm")) + list(Path(".").glob("*.litertlm"))
        if candidates:
            local_path = str(candidates[0])
        else:
            print(f"[ERRO] Nenhum arquivo .litertlm encontrado. Uso: push-model <caminho>")
            return False

    if not Path(local_path).exists():
        print(f"[ERRO] Arquivo não encontrado: {local_path}")
        return False

    print(f"[PUSH] Copiando {local_path} para dispositivo...")
    dest_dir = f"/data/data/{PACKAGE}/files/"
    dest_file = f"{dest_dir}{Path(local_path).name}"

    # Criar diretório se não existir
    run(f"shell 'run-as {PACKAGE} mkdir -p files'")
    run(f"shell 'run-as {PACKAGE} chmod 777 files'")

    # Push
    out, err, code = run(f"push {local_path} {dest_dir}", timeout=300)
    if code == 0:
        print(f"[OK] Modelo copiado para {dest_file}")

        # Verificar
        out2, err2, code2 = run(f"shell 'run-as {PACKAGE} ls -la files/'")
        print(f"[VERIFICAR] {out2}")
        return True
    else:
        print(f"[ERRO] {err or out}")
        return False

def cmd_devices():
    """Lista dispositivos."""
    out, err, code = run("devices")
    print(out or err)
    return code == 0

def cmd_shell(cmd_str):
    """Executa comando shell arbitrário."""
    print(f"[SHELL] {cmd_str}")
    out, err, code = run(f"shell {cmd_str}")
    if out:
        print(out)
    if err:
        print(f"[ERR] {err}")
    return out, err, code

def cmd_clear():
    """Limpa logcat."""
    out, err, code = run("logcat -c")
    print("[CLEAR] Logcat limpo")
    return True

def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return

    cmd = sys.argv[1].lower()

    if cmd == "stop":
        cmd_stop()
    elif cmd == "start":
        cmd_start()
    elif cmd == "logcat":
        filter_pat = sys.argv[2] if len(sys.argv) > 2 and not sys.argv[2].startswith("--") else None
        save = "--save" in sys.argv
        cmd_logcat(filter_pat, save)
    elif cmd == "install":
        cmd_install()
    elif cmd == "push-model":
        path = sys.argv[2] if len(sys.argv) > 2 else None
        cmd_push_model(path)
    elif cmd == "devices":
        cmd_devices()
    elif cmd == "shell" and len(sys.argv) > 2:
        cmd_shell(" ".join(sys.argv[2:]))
    elif cmd == "clear":
        cmd_clear()
    else:
        print(f"Comando desconhecido: {cmd}")
        print(__doc__)

if __name__ == "__main__":
    main()
