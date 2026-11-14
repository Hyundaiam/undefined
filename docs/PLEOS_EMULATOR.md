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

### 50상황 음성 재현

프로젝트의 **50상황 음성 테스트.command**를 실행하면 앱의 센서 재현 화면을 엽니다. 공식 콘솔의 차량은 정지·P단이어야 합니다. 캐릭터를 선택하고 좋은 25개/주의 25개에서 상황을 고른 뒤 **센서 상황 재현**을 누릅니다. 최대 31초 분량의 합성 입력을 약 5초로 재현하며, 같은 규칙 엔진이 감지한 결과와 해당 음성이 표시됩니다. 선택만으로 소리를 재생하지 않습니다. [전체 상황과 네 캐릭터 대사](SCENARIOS.md)를 참고하세요.

공식 Virtual Console이 제공하는 속도·벨트 등의 실제 SDK 시험은 아래의 60 시험도로를 사용합니다. 공식 콘솔이 제공하지 않는 보행자·손/발·차선·졸음 등은 곁의 50상황 시험 입력으로 확인합니다. 모든 50개가 공식 차량 센서에 이미 연결된 것은 아닙니다.

Mac에서 **60도로 음성 테스트.command**를 더블클릭하면 설치된 Pleos 에뮬레이터를 시작하거나 재사용하고, APK를 업데이트한 뒤 **60 시험도로 + 실제 SDK 속도 + 음성 켜짐**으로 시작합니다. 기존 운전자·녹음·기록을 삭제하지 않습니다. 일반 실행은 **곁 테스트 시작.command**로 기존 설정의 주행 화면을 엽니다.

### 60 도로에서 과속 경고를 듣는 방법

1. `60도로 음성 테스트.command`를 실행합니다. 앱 위쪽에 `에뮬레이터 시험도로 · 제한 60`, 오른쪽에 `코칭 중`이 보여야 합니다.
2. 브라우저에서 [Virtual Console](http://127.0.0.1:8088/)을 엽니다. 이 웹페이지는 차량 센서 값을 조작하는 공식 시험 도구이고, 앱은 별도 에뮬레이터 화면에 있습니다.
3. 콘솔에서 **D**를 고르고 **ACCEL을 4–5초 정도 길게** 누릅니다. 화면의 속도가 60을 넘으면 놓습니다.
4. 60 초과가 2초 유지되면 앱의 초과 속도·경고와 과속 음성이 나옵니다. 계속 과속하면 30초 간격으로 안내합니다.
5. **BRAKE**를 길게 눌러 60 이하로 낮추고 3초 유지하면 감속 격려 음성이 나옵니다.
6. 종료 시 BRAKE로 0까지 정지한 뒤 **P**를 고르고 앱의 `주행 종료`를 누릅니다.

시험 제한 60은 지도에서 읽은 값이 아닙니다. 이 debug 에뮬레이터 모드에만 명시적으로 넣는 시험값이며, 속도는 AAOS 차량 신호로 읽습니다. 시험 기록은 EMULATOR로 분리합니다. 실제 모드에서는 NaviHelper 제한속도가 없으면 도로 정보 대기를 표시합니다.

주행 중에는 큰 속도·짧은 안내·음성 켜기/끄기·주행 종료만 있는 화면을 사용합니다. 운전자·음성 캐릭터·녹음 파일·상황 선택은 **정차 중 설정**에서 바꿉니다. 주행 화면을 닫거나 다른 내비 앱을 열어도 시작한 서비스와 음성은 계속 동작합니다. `음성 꺼짐`, `녹음 파일 없음`, `다른 안내 대기`가 표시되면 해당 상태부터 확인하세요. 현재 과속/회복에는 네 캐릭터의 기존 임시 음성 파일이 연결되어 있습니다.

설치된 AVD 이름은 `Gyeot_Pleos_2_1`입니다. Android Studio의 Device Manager에서도 선택할 수 있습니다. RAM은 4 GB, 부팅은 Cold로 설정합니다. Quick boot에서는 VHAL을 이용할 수 없으므로 실행 스크립트는 snapshot을 사용하지 않습니다.

```sh
bash scripts/run_pleos_emulator.sh
```

앱 설치와 Virtual Console 연결은 에뮬레이터 부팅 후 별도 터미널에서 진행합니다. 장치가 여러 개라면 `adb devices`로 확인하고 아래의 장치 번호를 바꿉니다.

```sh
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 forward tcp:8088 tcp:8088
```

PC 브라우저에서 `http://127.0.0.1:8088/`을 열면 Virtual Console입니다. 기어·페달·조향·창문·탑승 상태 등으로 차량 신호를 변경합니다. 앱의 실제 차량·내비 모드를 확인하려면 정차 후 주행을 종료하고, `정차 중 설정 → 연결 · 설정`에서 **50상황 센서 재현과 에뮬레이터 시험도로를 모두 끈 뒤** 시작합니다.

## 선택적 실제 SDK 테스트

다음 테스트는 일반 Android 에뮬레이터와 CI에서 실행하지 않습니다. **개인 기록이 없는 별도 QA AVD**에서 명시적으로 실행합니다. Gradle 테스트가 앱을 제거할 수 있어 사용자 AVD에서는 실행하지 마세요. 여러 AVD 중 하나만 지정하는 직접 instrumentation 명령과 주행 음성 테스트는 [검증 기록](TESTING.md)을 참고하세요.

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
- [주행 제한 개발](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
