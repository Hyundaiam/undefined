# 곁: Drive Companion

내비게이션 옆에서 운전 습관을 기억하고, 친숙한 목소리로 짧게 피드백하는 **Pleos Connect용 네이티브 Android 프로토타입**입니다. 기존 웹 시연과 별도로 Kotlin 프로젝트를 구성했습니다.

## 실행과 다운로드

- [최신 APK](https://github.com/Hyundaiam/Hyundai/releases/latest) · 에뮬레이터 설치용 debug 서명 빌드
- [녹음 상황 15 + 15, 공통 멘트 8개](docs/RECORDING_GUIDE.md)
- [센서 지원 범위](docs/SENSOR_MATRIX.md) · [Gleo 연결](docs/GLEO_INTEGRATION.md) · [검증 범위](docs/TESTING.md)

처음에는 **시연 모드**입니다. `함께 출발`을 누른 뒤 좋은 순간/주의 순간을 선택하면 기록과 음성 흐름을 확인할 수 있습니다. 자동 시연은 15초마다 상황을 바꿉니다. `나의 운전`에서 이번·직전 주행의 좋은 이벤트 비율과 습관별 누적 기록을 비교합니다.

실차·Pleos 에뮬레이터에서는 `연결 · 설정`에서 시연 모드를 끄고 시작합니다. 차량에서 실제 제공되는 값만 읽으며, 미지원 항목은 미수신으로 표시합니다. 시작 후 `내비게이션으로 돌아가기`를 누르면 앱 화면을 내려도 foreground service가 계속 기록합니다. 알림의 `주행 종료`로 멈출 수 있습니다. 자동 부팅 실행이나 종료된 서비스의 무조건 재시작은 하지 않습니다.

## 구현한 기능

- 흑백 가로 화면, 운전 화면·운전 이력·음성/녹음·연결 설정
- 운전자 프로필 추가/선택, 차량 내부 Room DB 영구 저장, DataStore 설정
- 시연/차량 데이터를 분리한 이벤트·습관 누적과 주행 간 비교
- Vehicle SDK capability 확인 후 읽기 전용 구독, NaviHelper 도로 제한 속도, 선택적 AAOS 속도·기어 입력
- 사용자 시작 foreground service, 정상 종료/비정상 종료 후 주행 구간 복구
- 경상도 친구 / 욕쟁이 할머니 / 걱정 많은 엄마 / 5살 동생 선택
- Media3 파일 음성 재생, 오디오 포커스 상실 시 중지, 반복 안내 간격 제한
- 개인 녹음 파일 등록·유효성 검사·영구 저장, 제공받은 녹음의 APK 일괄 반영 도구
- Gleo 공식 Action/Callback 수신부와 세 가지 읽기 전용 함수의 App Schema

현재의 ‘학습’은 운전자별 관측·습관 통계와 이전 주행 비교입니다. 학습된 법규 판정 모델이나 완성된 ADAS 시스템으로 표시하지 않습니다. 도로 제한 속도는 내비게이션 값에 의존하며, 로컬 규칙은 코칭 후보를 만듭니다. 표본 비율은 안전 점수나 법규 준수율이 아닙니다.

## 음성 교체

현재 네 캐릭터에는 기존 프로젝트의 **임시 신경망 음성 파일**이 포함되어 있습니다. 브라우저 음성·Android 시스템 TTS를 호출하지 않습니다. 아직 사람 녹음이 들어간 상태는 아닙니다.

재생 순서는 **앱에서 등록한 파일 → APK에 포함한 사람 녹음 → 정확히 대응하는 임시 파일**입니다. 대응 파일이 없으면 녹음 대기이며 다른 상황의 대사를 재생하지 않습니다. 기존 일부 대사에는 ‘60 도로’, ‘지난번보다 좋아졌다’가 포함되어 있어 실차 자동 안내에서는 해당 조건이 확인되지 않으면 사용하지 않습니다.

1. [녹음 안내](docs/RECORDING_GUIDE.md)에 맞춰 `friend/G01.wav`, `mom/B01.mp3`처럼 준비합니다. 일부 상황부터 전달해도 됩니다.
2. 앱의 `목소리 · 녹음`에서 캐릭터와 상황을 선택해 파일을 등록하거나, 개발자가 아래 명령으로 APK에 포함합니다.

```sh
python3 scripts/import_recordings.py --source incoming-recordings --check-only
python3 scripts/import_recordings.py --source incoming-recordings
python3 scripts/verify_contracts.py
./gradlew :app:assembleDebug
```

일괄 도구에는 `ffprobe`가 필요합니다. 음성은 0.5–30초, 파일당 10 MB 이하로 검사합니다. 기존 등록 파일은 새 파일 검증이 끝난 뒤에만 교체합니다. 기기에 직접 등록한 파일은 다음 APK 업데이트 후에도 우선합니다. 다른 제공자를 사칭하거나 실존 인물의 음성을 복제하는 기능은 포함하지 않았습니다.

## 기술 스택과 빌드

| 항목 | 버전/구성 |
| --- | --- |
| 언어·UI | Kotlin 1.9.25, Jetpack Compose BOM 2024.09.03 |
| Android | minSdk 28 · targetSdk 34 · compileSdk 35 |
| 빌드 | Java 17 · Gradle wrapper 8.9 · AGP 8.7.3 |
| Pleos | Vehicle 2.3.2 · NaviHelper 3.0.5 · FusedLocation 1.1.3 |
| 데이터·실행 | Room 2.6.1 · DataStore 1.1.1 · Coroutines 1.8.1 |
| 음성 | Media3 ExoPlayer 1.4.1, 로컬 오디오 파일 |

Android Studio에서 이 폴더를 열고 Android SDK Platform 35와 Build Tools를 설치합니다. SDK 경로는 로컬의 `local.properties`에 `sdk.dir=...`로 설정합니다. SDK와 서명 키는 Git에 넣지 않습니다.

```sh
python3 scripts/verify_contracts.py
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Pleos 의존성은 공식 Maven 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`에서 받습니다. 별도 API 키나 서버 없이 시연 가능합니다. GitHub Actions도 테스트·Lint·APK 빌드를 실행합니다. 배포용 release 서명과 마켓 심사는 별도이며 공개 APK는 개발·시연용입니다.

## 차량 연동의 현재 범위

손의 개수, 양발 사용, 앞차 거리/차종, 보행자, 차선 이탈, 터널 인지는 현재 확인한 SDK 신호만으로 만들지 않았습니다. 추가 실내 센서·ADAS·도로 데이터가 필요합니다. FusedLocation은 지원 여부만 확인하고 위치를 수집하지 않습니다.

공개 Gleo 문서는 **사용자 음성 요청 → Gleo 함수 호출 → 앱 데이터 응답 → Gleo 최종 답변**을 정의합니다. 지속적인 센서 스트림을 앱에서 Gleo에 보내 자동 판단받는 API는 확인되지 않았습니다. 해당 부분은 공급사 API와 접근 권한이 확보되어야 완성할 수 있습니다. 현재의 로컬 규칙을 Gleo 판단 결과로 표시하지 않습니다.

## 공식 기준과 자료

- [공모전 개발 안내](https://appdevchallenge.auto-hmg.io/guide/development), [제출 FAQ](https://appdevchallenge.auto-hmg.io/faq)
- [Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), [Capability 모델](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/entry)
- [Pleos 에뮬레이터 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Gleo Core Concepts](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts)

SDK와 외부 의존성에는 각 공급자의 약관·라이선스가 적용됩니다. [임시 음성 출처](docs/VOICE_PROVENANCE.md)를 참고하세요. 이 저장소 공개가 별도의 소스 재사용 라이선스 부여를 의미하지는 않습니다.
