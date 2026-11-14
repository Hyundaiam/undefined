# 검증 기록

## v0.5.0 음성 구체와 설정

2026-11-14, Pleos Connect 2.1.0 QA AVD에서 JVM 79개(기존 76개 + 음성 진폭/시각 검사 3개), 기기 10개(기존 기본/SDK/웹 8개 + 구체 음성/설정 2개)를 검증했습니다. Lint·앱/테스트 APK 빌드와 200개 음성 계약 검사도 통과했습니다.

- 웹 센서로 과속 B02 판정 → 실제 Media3 음성 재생 → 재생 시각에 맞춘 PCM 진폭이 구체까지 전달되고 여러 강약 값으로 변하는지 확인
- 미리 디코딩된 음성이 현재 무음 구간을 미리 움직이지 않는지, PCM 버퍼를 변경하지 않는지, 중단 시 진폭이 0이 되는지 검사
- 실제 Compose 설정에서 엄마 목소리 선택·저장 → 해당 목소리 미리 듣기 → 음소거 즉시 중단 → 다시 켜기 → Activity 재생성 후 선택 유지
- PixelCopy로 앱의 대기 구체·발화 구체·설정 화면을 저장해 무채색과 화면 구성을 확인
- 기존 HTTP 입력·연결 끊김·DB 보존·백그라운드 수집·Pleos SDK 입력 검사를 다시 통과

```sh
adb -s emulator-5556 shell am instrument -w -r -e class io.hyundaiam.gyeot.CompanionSpeechTest,io.hyundaiam.gyeot.VoiceSettingsTest,io.hyundaiam.gyeot.WebSimulatorTest,io.hyundaiam.gyeot.LocalLearningTest,io.hyundaiam.gyeot.RecordingAndServiceTest,io.hyundaiam.gyeot.PleosRuntimeTest -e companionUi true -e webSimulator true -e pleosIntegration true io.hyundaiam.gyeot.test/androidx.test.runner.AndroidJUnitRunner
```

새 UI는 [음성 구체 안내](COMPANION_UI.md)를 참고하세요. 이전 버전의 화면·검증 기록은 아래에 보존했습니다.

## v0.4.0 웹 가상주행

2026-11-14, 같은 Pleos QA AVD에서 검증했습니다. JVM 76개(기존 72개 + 웹 센서 계약 4개), 기기 8개(기존 기본/SDK 7개 + 웹 브리지 1개), Lint·APK·기기 테스트 APK 빌드와 200개 음성 계약 검사가 통과했습니다. 아래 v0.3.0의 134개 결과는 해당 버전의 별도 기록입니다.

- 웹 요청의 시각/출처/판정 ID 위조 거부, 수치·타입 검증, 서버 시각과 실제 조건 유지 시간 사용
- HTTP 원시 센서 입력 → 네이티브 B02 과속 음성 → C06 감속 격려의 실제 음성 트랙 진행
- 입력 중단 1.6초 후 프레임 해제·음성 중지·대기 복귀, 초기화, 다른 Origin 요청 거부
- 브라우저에서 실제 페달을 눌러 89 km/h로 가속 → 과속 안내, 같은 60 도로에서 감속 → 격려
- 도로가 이어지며 보호구역·차간거리 등 조건 자동 변경, 웹에서 할머니 선택·벨트 해제 → 해당 캐릭터의 벨트 음성

```sh
adb -s emulator-5556 shell am instrument -w -r -e class io.hyundaiam.gyeot.WebSimulatorTest -e webSimulator true io.hyundaiam.gyeot.test/androidx.test.runner.AndroidJUnitRunner
```

웹 전송은 센서 값만 받습니다. 웹에서 지정한 상황 ID를 바로 재생하는 endpoint는 없습니다. [웹 가상주행 안내](WEB_SIMULATOR.md)를 참고하세요.

## v0.3.0 상황 재현

검증 환경: macOS Apple Silicon, Java 17, Gradle 8.9, Android SDK 35. 2026-11-14 KST에 공식 Pleos Connect 2.1.0 / Android API 34 ARM64에서 v0.3.0을 검증했습니다.

## 자동 검증

| 검증 | 범위 |
| --- | --- |
| 코어 JVM 68개 | 25+25 카탈로그, 모든 50개 센서 시퀀스가 해당 상황만 판정하는지 검사, 신호 신선도/출처, 벨트/급제동 조건, 통계, 과속/감속, 음성 간격/우선순위 |
| Gleo JVM 4개 | Action JSON/이중 인코딩, Callback 문자열, 잘못된 입력 거부, 명시적 패키지 형식 |
| Android 기기 5개 | DB 재오픈/운전자/시연 분리, 타 운전자 이벤트 거부, 중단 주행 복구, 잘못된 음성 파일 등록 시 기존 파일 유지, 화면을 내린 뒤 서비스 유지 |
| Pleos 전용 2개 | 실제 SDK 속도·기어·창문·벨트 수신, 미제공 신호의 null 유지, 백그라운드 신호 갱신·저장 |
| 주행 중 음성 1개 | 공식 콘솔의 가속/감속으로 60 시험도로 과속 B02·회복 C06 음성 진행, 실제 UX 제한 아래 화면 유지, 주행 화면을 종료한 뒤 재과속 음성, 시험 출처 분리 |
| 상황 음성 기기 53개 | 친구 목소리로 50개 센서 재현→DB 판정→실제 음성 트랙 진행. 나머지 3캐릭터도 B16을 각각 재현 |
| Compose UI 1개 | 상황 선택만으로는 소리가 나지 않고, 센서 재현을 누르면 선택한 B17 판정과 음성이 나오는지 확인 |
| 계약 검사 | 58개 녹음 슬롯/파일/manifest, 서로 다른 상황 음성 200개와 대사·해시·길이, Gleo 3함수, 제어 권한/시스템 TTS 미사용, 주행/설정 Activity 분리, PCM pre-roll |
| 로컬 음성 내용 검사 | 한국어 ASR로 200개 전체 발화 확인, 5개 수정 후 재검사. 연기/자연스러움의 인증은 아님 |
| Android Lint·APK | debug 정적 검사, 앱/기기 테스트 APK 생성 |

코어 68 + Gleo 4 = JVM 72개입니다. 기기 검증은 기본 7개, 50상황 음성 53개, UI 1개, 주행 음성 1개로 62개입니다. 합계 134개입니다. Pleos/상황 음성 테스트는 명시적으로 켠 경우에만 실행합니다. 일반 CI는 JVM 검사·Lint·앱 및 기기 테스트 APK 빌드까지 수행합니다.

```sh
python3 scripts/verify_contracts.py
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

파일 등록 테스트용 0.8초 WAV는 테스트 코드에서만 만들며 음성 팩에 포함하지 않습니다. 실제 안내에는 기존 프로젝트의 임시 MP3 파일을 사용하고 사람 녹음은 아직 받지 않았습니다.

## Pleos에서 확인한 결과

| 항목 | 결과 |
| --- | --- |
| 시스템 이미지 | RELEASE.release_connect_v2.1.0, API 34, ARM64 |
| 속도·기어 | 공개 AAOS 속도·기어 조회. 공식 Virtual Console의 가속/감속 변화 수신 |
| 좌석·창문·조향·브레이크·방향지시등 | SDK 현재값 수신. 운전석 창문 변경 콜백도 확인 |
| NaviHelper | 서비스 연결 성공. 경로가 없어 도로 제한속도 미수신. 실제 모드에서 임의의 60을 넣지 않음 |
| 60 시험도로 | 명시적 에뮬레이터 모드에만 시험 제한 60 적용, SDK 속도로 평가, EMULATOR 출처로 저장 |
| 주행 화면 | 실제 UX 제한 활성화 중 DriveActivity가 RESUMED·window focused 상태 유지. 설정 화면은 제한 대상 유지 |
| 과속·회복 음성 | B02와 C06 파일의 실제 음성 트랙에서 재생 위치 100ms 이상 진행 확인. 재생 요청이나 pre-roll 진행만으로 통과시키지 않음 |
| 백그라운드 | 실제 SDK 입력 갱신·DB 저장, 주행 화면 종료 후 재과속 B02 재생 진행 확인 |
| 미제공 신호 | 손 개수·양발·앞차 거리·터널은 null 유지 |
| Gleo | ai.umos.ambientai 4.2.0.15 설치 확인. 최종 음성 답변은 검증하지 않음 |

테스트는 스피커 음질이나 사용자가 실제 들었는지까지 증명하지 않습니다. 차량의 오디오 그룹·음량·다른 안내와의 조합은 별도 확인이 필요합니다. 이 결과가 마켓 UX 승인이나 실제 차량 인증을 뜻하지 않습니다.

## 기기 테스트 재현

**사용자 프로필·녹음이 없는 별도 QA AVD에서 실행하세요.** Gradle connected 테스트는 앱을 설치·제거할 수 있습니다. 사용자 AVD의 업데이트는 `adb install -r` 또는 프로젝트의 실행 `.command`로만 진행합니다. 이번 검증에서도 사용자 AVD와 QA AVD를 분리했습니다.

```sh
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 forward tcp:8089 tcp:8088
adb -s emulator-5556 shell am instrument -w -r -e class io.hyundaiam.gyeot.LocalLearningTest,io.hyundaiam.gyeot.RecordingAndServiceTest,io.hyundaiam.gyeot.PleosRuntimeTest -e pleosIntegration true io.hyundaiam.gyeot.test/androidx.test.runner.AndroidJUnitRunner
```

50개 전체 음성과 UI를 확인하려면 같은 QA AVD에 설치한 뒤 실행합니다. 선택하는 ID는 센서 시퀀스만 고르고, 판정 결과를 직접 저장하거나 직접 재생하지 않습니다.

```sh
adb -s emulator-5556 shell am instrument -w -r -e class io.hyundaiam.gyeot.ScenarioVoiceTest,io.hyundaiam.gyeot.SimulatorPanelTest -e scenarioVoices true io.hyundaiam.gyeot.test/androidx.test.runner.AndroidJUnitRunner
```

`GyeotScenarioTest` 로그의 `sensor -> verdict -> voice position advanced`와 54개 성공을 확인합니다. UI 테스트는 앱 내부에 `scenario-panel.png`를 저장합니다.

기기 번호는 실제 QA AVD에 맞게 바꿉니다. Pleos의 활성 운전자는 시스템 사용자 0과 다를 수 있어 테스트 내부에서도 `pm grant --user current`로 속도 권한을 설정합니다.

주행 음성 테스트는 [에뮬레이터 안내](PLEOS_EMULATOR.md)의 60 시험 절차를 따릅니다. QA 콘솔을 열고 별도 터미널에서 실행합니다.

```sh
adb -s emulator-5556 shell am instrument -w -r -e class io.hyundaiam.gyeot.SpeedFeedbackTest -e pleosSpeedFeedback true io.hyundaiam.gyeot.test/androidx.test.runner.AndroidJUnitRunner
```

`adb -s emulator-5556 logcat -s GyeotSpeedTest:I`에서 다음 순서로 진행합니다.

1. `READY_FOR_ACCEL`: D를 고르고 ACCEL을 길게 눌러 60을 초과합니다.
2. `SPEED_WARNING_RENDERED`, `READY_FOR_BRAKE`: BRAKE를 길게 눌러 60 이하로 감속합니다.
3. `RECOVERY_VOICE_RENDERED`, `READY_FOR_BACKGROUND_ACCEL`: 주행 화면 종료 후 다시 60을 초과합니다.
4. `BACKGROUND_WARNING_RENDERED`, `OK (1 test)`를 확인하고 BRAKE로 정지한 뒤 P로 복귀합니다.

테스트 APK는 앱 내부에 `speed-warning.png`, `speed-recovered.png`를 PixelCopy로 저장합니다. 이 검증은 시험도로 제한과 SDK 속도를 조합한 것이며 실제 지도 경로의 제한속도 검증을 대신하지 않습니다.

## 남은 검증

- 실제 도로별 NaviHelper 제한속도, 차종별 capability/권한, 연결 중단·복구
- 실제 Gleo Schema 승인, Action 호출 및 최종 음성 답변
- 실제 내비와의 오디오 포커스·겹침, 차량 스피커 음질
- 최종 차량 레이아웃·터치 영역, Pleos 마켓의 주행 UX 심사
- 별도 손/발·ADAS·차선·보행자·터널 데이터 공급 및 계약
