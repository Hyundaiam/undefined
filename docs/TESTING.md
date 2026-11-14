# 검증 기록

검증 환경: macOS Apple Silicon, Java 17, Gradle 8.9, Android SDK 35. 2026-11-14 KST에 공식 Pleos Connect 2.1.0 / Android API 34 ARM64에서 v0.2.0을 검증했습니다.

## 자동 검증

| 검증 | 범위 |
| --- | --- |
| 코어 JVM 17개 | 15+15 카탈로그, 신호 신선도/출처, 벨트/급제동 조건, 통계, 제한 초과 2초·감속 3초·재경고, 누락 제한속도, 도로 제한 변경 시 가짜 개선 방지, 음성 간격/우선순위 |
| Gleo JVM 4개 | Action JSON/이중 인코딩, Callback 문자열, 잘못된 입력 거부, 명시적 패키지 형식 |
| Android 기기 5개 | DB 재오픈/운전자/시연 분리, 타 운전자 이벤트 거부, 중단 주행 복구, 잘못된 음성 파일 등록 시 기존 파일 유지, 화면을 내린 뒤 서비스 유지 |
| Pleos 전용 2개 | 실제 SDK 속도·기어·창문·벨트 수신, 미제공 신호의 null 유지, 백그라운드 신호 갱신·저장 |
| 주행 중 음성 1개 | 공식 콘솔의 가속/감속으로 60 시험도로 과속 B02·회복 C06 음성 진행, 실제 UX 제한 아래 화면 유지, 주행 화면을 종료한 뒤 재과속 음성, 시험 출처 분리 |
| 계약 검사 | 38개 녹음 슬롯/파일/manifest, Gleo 3함수, 제어 권한/시스템 TTS 미사용, 주행/설정 Activity 분리, PCM pre-roll |
| Android Lint·APK | debug 정적 검사, 앱/기기 테스트 APK 생성 |

코어 17 + Gleo 4 + 기기 8 = 총 29개입니다. Pleos/주행 음성 테스트는 명시적으로 켠 경우에만 실행합니다. 일반 CI는 JVM 검사·Lint·앱 및 기기 테스트 APK 빌드까지 수행합니다.

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
