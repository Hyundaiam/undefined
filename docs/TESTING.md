# 검증 기록

검증 환경: macOS Apple Silicon, Java 17, Gradle 8.9, Android SDK 35. 일반 Android API 36 ARM64 환경에 이어, 2026-11-14 KST에 공식 Pleos Connect 2.1.0 / Android API 34 ARM64에서도 검증했습니다.

## 실행한 자동 검증

| 검증 | 범위 |
| --- | --- |
| 코어 JVM 11개 | 15+15 카탈로그, 미수신/오래된 속도 제외, 조건 유지/재발 간격, 탑승 확인 없는 벨트 경고 방지, 긴급/미확인 급제동 제외, 입력 출처 변경, 음성 간격, 빈 이력 비율 |
| Gleo JVM 4개 | Action JSON/이중 인코딩, Callback data 문자열, 잘못된 입력 거부, 명시적 패키지 형식 |
| Android 기기 5개 | DB 재오픈 후 운전자/시연 데이터 분리, 타 운전자 주행에 이벤트 저장 거부, 중단 주행 복구, 잘못된 음성 파일 교체 시 기존 파일 유지, 화면을 내린 뒤 서비스 heartbeat 지속·정상 종료 |
| Pleos 전용 2개 | 실제 SDK의 속도·기어·창문·벨트 입력과 미제공 신호의 null 유지, 실제 SDK 신호가 화면을 내린 뒤에도 갱신·저장되는지 검증 |
| 계약 검사 | 38개 슬롯의 ID/상황/음성 파일, 사람 음성 manifest 경로, Gleo 3함수와 Manifest 일치, 제어 권한/시스템 TTS 미사용 |
| Android Lint·APK | debug 정적 검사와 설치 APK 생성 |

```sh
python3 scripts/verify_contracts.py
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest
```

파일 import의 테스트 음원은 테스트 코드에서 만든 0.8초 WAV이며 사용자용 음성 팩에는 포함하지 않습니다. 기기 테스트는 테스트 환경의 기록만 만듭니다.

## Pleos 2.1.0에서 확인한 결과

JVM 15개와 기기 테스트 7개, 총 22개가 통과했습니다. Pleos 전용 테스트는 `pleosIntegration=true`를 지정한 경우에만 실행됩니다. 일반 Android와 CI에서는 해당 두 테스트를 건너뛰며 테스트 APK는 빌드합니다.

| 항목 | 확인 결과 |
| --- | --- |
| 시스템 이미지 | RELEASE.release_connect_v2.1.0, API 34, ARM64 |
| AAOS 속도·기어 | 정차 속도 0.0 km/h와 GEAR_PARK 실제 조회 |
| 창문 | 4개 영역 수신. Virtual Console에서 운전석 창문을 열었을 때 앱의 원시 위치 변경 콜백 수신 |
| 좌석 | 탑승·벨트 5개 영역, 등받이 4개 영역 수신 |
| 조향각·브레이크·방향지시등 | 현재 값 수신 확인 |
| NaviHelper | 서비스 연결 성공. 테스트 경로가 없어 도로 제한 속도는 미수신 |
| 백그라운드 | 실제 SDK 모드에서 화면을 내린 뒤 속도 입력 timestamp 갱신과 PLEOS 관측의 DB 저장 확인 |
| 미제공 신호 | 손 개수·양발·앞차 거리·터널 상태는 null 유지 |
| Gleo | 이미지에 ai.umos.ambientai 4.2.0.15 설치 확인. 최종 답변 기능은 검증하지 않음 |

이 이미지는 headless 시스템 사용자와 활성 운전자 사용자를 구분합니다. 테스트 권한은 `pm grant --user current`로 활성 운전자에게 부여합니다. 테스트 도구는 앱을 새로 설치·제거할 수 있으므로 외부에서 미리 준 권한 대신 테스트 내부에서도 해당 권한을 설정합니다. 일반 앱 실행 시에는 화면의 정상 런타임 권한 요청을 사용합니다.

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.pleosIntegration=true
```

창문 변경을 직접 재현하려면 [전용 실행 안내](PLEOS_EMULATOR.md)의 선택적 콜백 테스트를 사용합니다. 이미지와 SDK 파일은 GitHub에 재배포하지 않습니다.

## 별도 검증이 필요한 항목

- 실제 차종별 capability/권한/영역 값, 연결 중단·복구, 주행 중 비영(非零) 속도와 경로별 제한 속도
- 실제 Gleo 앱 패키지 및 Schema 승인, Action 수신과 최종 음성 답변
- 실제 내비 앱과 오디오 포커스 우선순위·안내 겹침, 차량 스피커 음량/음질
- 전용 디스플레이에서 레이아웃과 터치 영역의 시각 검수, 주행 중 UX 제한과 마켓 심사
- 공급받을 손/발·ADAS·터널/차선/보행자 데이터 계약

에뮬레이터 결과를 실제 차량 센서 전체나 Gleo 연동 완료로 간주하지 않습니다. 전용 Pleos 이미지는 SDK 이용약관 동의 후 별도로 설치해야 하며, 현재 작업 환경에서는 동의·설치·부팅을 완료했습니다.

## 시연 확인 순서

1. APK 설치 → 운전자 선택 → `함께 출발` → 좋은/주의 상황 선택
2. `목소리 · 녹음`에서 네 캐릭터의 기존 임시 음성 확인, 미연결 상황은 녹음 대기 확인
3. `나의 운전`에서 기록 확인 → 종료 → 새 운전자 추가 → 기록 분리 확인
4. WAV/MP3 등록 → 해당 상황 미리 듣기 → 앱 재실행 후 등록 유지 확인
5. 주행 시작 → 화면의 `내비게이션으로 돌아가기` → 알림/기록 유지 확인 → 알림에서 종료
6. Pleos 환경에서는 시연 모드를 끄고 센서의 실제 지원/미수신 상태부터 확인
