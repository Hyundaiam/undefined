#!/usr/bin/env python3
"""Start the installed Pleos test environment without clearing application data."""
import os
from pathlib import Path
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or Path.home() / "Library/Android/sdk")
ADB = SDK / "platform-tools/adb"
AVD = "Gyeot_Pleos_2_1"


def adb(*args, check=True):
    return subprocess.run([str(ADB), *args], check=check, capture_output=True, text=True)


def available(port):
    try:
        with socket.socket() as connection:
            connection.bind(("127.0.0.1", port))
        return True
    except OSError:
        return False


def main():
    if not ADB.is_file():
        raise SystemExit("Android SDK를 찾지 못했어요. ANDROID_HOME 경로를 확인해 주세요.")
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    if not apk.is_file():
        raise SystemExit("먼저 프로젝트에서 ./gradlew :app:assembleDebug 를 실행해 APK를 만들어 주세요.")
    serial = None
    for line in adb("devices").stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) == 2 and parts[0].startswith("emulator-") and parts[1] == "device":
            if adb("-s", parts[0], "emu", "avd", "name", check=False).stdout.splitlines()[0:1] == [AVD]:
                serial = parts[0]
                break
    if serial is None:
        port = next((p for p in range(5554, 5586, 2) if available(p) and available(p + 1)), None)
        if port is None:
            raise SystemExit("에뮬레이터용 포트가 비어 있지 않아요.")
        serial = f"emulator-{port}"
        log = ROOT / ".preview/pleos-test-launch.log"
        log.parent.mkdir(exist_ok=True)
        with log.open("ab") as output:
            subprocess.Popen(["bash", str(ROOT / "scripts/run_pleos_emulator.sh"), AVD, "-port", str(port)], stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    print("Pleos 부팅을 기다리는 중이에요…", flush=True)
    for _ in range(180):
        if adb("-s", serial, "shell", "getprop", "sys.boot_completed", check=False).stdout.strip() == "1":
            break
        time.sleep(1)
    else:
        raise SystemExit("부팅이 지연되고 있어요. .preview/pleos-test-launch.log를 확인해 주세요.")
    adb("-s", serial, "install", "-r", str(apk))
    forwards = [line.split() for line in adb("forward", "--list").stdout.splitlines()]
    if [serial, "tcp:8088", "tcp:8088"] not in forwards:
        adb("-s", serial, "forward", "--no-rebind", "tcp:8088", "tcp:8088")
    adb("-s", serial, "shell", "am", "start", "--user", "current", "-n", "io.hyundaiam.gyeot/.MainActivity")
    print("곁 앱을 열었어요. ‘함께 출발’을 누르면 시연을 시작합니다.")
    print("차량 신호 테스트 콘솔: http://127.0.0.1:8088/")
    print("실제 SDK 신호를 보려면 앱의 ‘연결 · 설정’에서 시연 모드를 꺼주세요.")


if __name__ == "__main__":
    main()
