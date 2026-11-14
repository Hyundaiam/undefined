#!/usr/bin/env python3
"""Start the installed Pleos test environment without clearing application data."""
import os
import argparse
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
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--speed-test", action="store_true", help="Start the clearly labelled 60 km/h emulator test road with voice enabled.")
    modes.add_argument("--scenario-test", action="store_true", help="Open the 50-situation sensor replay panel with the four scenario voice packs.")
    modes.add_argument("--web-simulator", action="store_true", help="Start continuous web driving with native app feedback at localhost:8090.")
    args = parser.parse_args()
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
    # Android reports boot_complete before Pleos starts its console and navigation.
    # Launch after those services are ready so the first-run navigation does not cover us.
    print("차량 테스트 콘솔이 준비되기를 기다리는 중이에요…", flush=True)
    for _ in range(60):
        if ":8088" in adb("-s", serial, "shell", "ss", "-ltn", check=False).stdout:
            break
        time.sleep(1)
    else:
        raise SystemExit("차량 콘솔 준비가 지연되고 있어요. 잠시 후 실행 파일을 다시 열어 주세요.")
    if args.web_simulator:
        forwards = [line.split() for line in adb("forward", "--list").stdout.splitlines()]
        if [serial, "tcp:8090", "tcp:8765"] not in forwards:
            adb("-s", serial, "forward", "--no-rebind", "tcp:8090", "tcp:8765")
    if args.speed_test or args.scenario_test or args.web_simulator:
        for permission in (["android.permission.POST_NOTIFICATIONS"] + (["android.car.permission.CAR_SPEED"] if args.speed_test else [])):
            adb("-s", serial, "shell", "pm", "grant", "--user", "current", "io.hyundaiam.gyeot", permission)
    launch = ["-s", serial, "shell", "am", "start", "--user", "current", "-n", "io.hyundaiam.gyeot/" + (".MainActivity" if args.scenario_test else ".DriveActivity")]
    if args.speed_test: launch += ["--ez", "speedTest", "true"]
    if args.scenario_test: launch += ["--ez", "simulatorTest", "true"]
    if args.web_simulator: launch += ["--ez", "webSimulator", "true"]
    adb(*launch)
    print("곁 앱을 열었어요. 설정·운전자·목소리는 정차 중 변경할 수 있어요.")
    print("차량 신호 테스트 콘솔: http://127.0.0.1:8088/")
    if args.web_simulator:
        print("웹 가상주행: http://127.0.0.1:8090/ · 출발한 뒤 가속·제동·도로·차량 조건을 바꾸면 곁 앱이 음성으로 안내해요.")
    elif args.scenario_test:
        print("50상황 센서 재현을 열었어요. 공식 콘솔의 차량은 정지·P단이어야 이 설정 화면을 사용할 수 있어요.")
        print("좋은 상황 25 / 주의 상황 25에서 상황을 고르고 ‘센서 상황 재현’을 누르세요. 감지 결과와 음성 재생 상태가 표시돼요.")
    elif args.speed_test:
        print("60 시험도로와 음성을 켰어요. 이미 다른 주행이 실행 중이면 앱에서 종료한 뒤 이 파일을 다시 실행해 주세요.")
        print("콘솔에서 D → ACCEL 길게 → 60 초과 2초 유지: 과속 안내 / BRAKE → 60 이하 3초 유지: 격려 안내")
    else:
        print("‘함께 출발’로 기존 설정을 시작해요. 과속 음성 시험은 ‘60도로 음성 테스트.command’를 실행해 주세요.")


if __name__ == "__main__":
    main()
