# Pleos 전용 에뮬레이터

공식 Pleos Connect 2.1.0 ARM64 시스템 이미지는 Android API 34 기반입니다. Mac에서는 공식 SDK Update Site를 사용합니다.

```text
https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml
```

42dot SDK 약관에 동의한 뒤 SDK Manager에서 다음 패키지를 설치합니다. 이 저장소에는 시스템 이미지와 약관 동의 파일을 포함하지 않습니다.

```sh
sdkmanager 'system-images;android-34;pleos_car_emulator;arm64-v8a'
```

## 실행

설치된 AVD 이름은 `Gyeot_Pleos_2_1`입니다. Android Studio의 Device Manager에서도 선택할 수 있습니다. RAM은 4 GB, 부팅은 Cold로 설정합니다. Quick boot에서는 VHAL을 이용할 수 없으므로 실행 스크립트는 snapshot을 사용하지 않습니다.

```sh
bash scripts/run_pleos_emulator.sh
```

앱 설치와 Virtual Console 연결은 에뮬레이터 부팅 후 별도 터미널에서 진행합니다. 장치가 여러 개라면 `adb devices`로 확인하고 아래의 장치 번호를 바꿉니다.

```sh
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 forward tcp:8088 tcp:8088
```

PC 브라우저에서 `http://127.0.0.1:8088/`을 열면 Virtual Console입니다. Motion의 기어·브레이크·조향각, Convenience의 창문·탑승 상태 등으로 차량 신호를 변경할 수 있습니다. 앱에서는 `연결 · 설정`의 시연 모드를 끄고 주행을 시작해 제공되는 신호를 확인합니다.

## 선택적 실제 SDK 테스트

다음 테스트는 일반 Android 에뮬레이터와 CI에서 실행하지 않습니다. 공식 Pleos 에뮬레이터를 부팅한 뒤 명시적으로 실행합니다. 테스트는 앱을 설치하고 활성 운전자의 CAR_SPEED 권한을 설정합니다.

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.hyundaiam.gyeot.PleosRuntimeTest \
  -Pandroid.testInstrumentationRunnerArguments.pleosIntegration=true
```

테스트는 실제 SDK 프레임에서 속도·기어·창문·벨트 값과 백그라운드 수집·저장을 확인하고, 미제공 손/발·ADAS 입력이 임의로 채워지지 않는지 검사합니다. 2026-11-14 실행 결과와 제한 사항은 [검증 기록](TESTING.md)에 있습니다.

실시간 창문 변경 콜백을 직접 확인하려면 수신 테스트만 다음과 같이 실행합니다.

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.hyundaiam.gyeot.PleosRuntimeTest#receivesVehicleSignalsFromThePleosRuntime \
  -Pandroid.testInstrumentationRunnerArguments.pleosIntegration=true \
  -Pandroid.testInstrumentationRunnerArguments.pleosObserveChange=true
```

`GyeotPleosTest` 로그에 `READY_FOR_WINDOW_CHANGE`가 나오면 60초 안에 Virtual Console의 Window Position에서 Front left 값을 바꿉니다. `WINDOW_CHANGE_RECEIVED`와 테스트 성공을 확인한 뒤 창문을 다시 닫습니다. 테스트 도구가 앱을 제거할 수 있으므로 종료 후 일반 사용용 APK를 다시 설치합니다.

이 이미지의 활성 운전자는 시스템 사용자 0과 다를 수 있습니다. 개발 중 수동으로 속도 권한을 부여해야 할 때는 `adb shell pm grant --user current io.hyundaiam.gyeot android.car.permission.CAR_SPEED`를 사용합니다. 기본 SDK 테스트는 음성 안내를 끄고 수행하며, Gleo의 최종 답변 기능을 대신 검증하지 않습니다.

## 공식 자료

- [이미지 설치와 AVD 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Cold boot와 에뮬레이터 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)
- [Virtual Console 연결](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [Virtual Console 사용](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)
