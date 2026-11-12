# Gleo 연결

공식 Gleo Interface는 사용자 음성 명령을 처리하는 function calling 방식입니다. 앱이 센서 이벤트를 계속 전송해 Gleo 판단을 요청하는 추론 API와는 구분됩니다. 이 저장소는 공개된 App Schema, Action, Callback 규격을 구현합니다.

## 제공 함수

`app/src/main/assets/schema.json`에 세 가지를 선언합니다. `component: service`는 가이드에 따라 BroadcastReceiver로 수신합니다. 세 함수는 모두 데이터 조회이며 운전자 변경·주행 시작·차량 제어는 하지 않습니다.

| 함수 | 응답 |
| --- | --- |
| GET_DRIVING_CONTEXT | 현재 선택 운전자의 진행 중인 세션, 속도/제한 속도, 최근 로컬 코칭 후보와 근거 |
| GET_DRIVING_PROGRESS | 해당 운전자·입력 출처의 좋은/주의 이벤트 수와 습관별 집계 |
| GET_RECORDING_STATUS | 캐릭터당 기본 30/공통 8슬롯, 개인 파일/포함된 사람 녹음 수 |

이름·좌표·녹음 파일은 보내지 않습니다. 응답에는 DEMO/PLEOS 출처를 포함합니다. 코칭 후보는 `LOCAL_RULES_NOT_GLEO`로 명시합니다. 임의의 AI 확정 판정으로 포장하지 않습니다.

## 연결 순서

1. 공식 가이드에 따라 프로젝트/앱과 App Schema의 Gleo 연동 승인을 준비합니다.
2. APK를 설치하고 곁에서 운전자를 선택합니다.
3. 차량에 실제 설치된 Gleo 앱의 패키지명을 확인한 뒤 `연결 · 설정`에 입력합니다. 문서에 확정되지 않은 패키지명을 하드코딩하지 않았습니다.
4. `Gleo에 운전 요약 제공`을 켭니다. 기본값은 꺼짐입니다.
5. Gleo에서 곁의 운전 상태/습관/녹음 상태를 음성으로 요청하여 Action 수신과 Callback 최종 답변을 확인합니다. 승인 전에는 schema 파일을 넣었다는 것만으로 기능이 활성화되지 않습니다.

Action의 `ACTION_PARAM`은 JSON 객체 문자열이며 문서 예시의 이중 인코딩도 허용합니다. Callback은 `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` 액션, `packageName`과 `response` extras를 사용합니다. `response.data`는 **JSON 배열을 인코딩한 문자열**입니다. `status`, `message`, `mode`도 공식 규격을 따릅니다. 처리 제한은 문서의 7초보다 짧은 6초입니다.

Callback Intent에는 설정한 Gleo 패키지를 명시합니다. 불특정 앱으로 운전자 데이터를 브로드캐스트하지 않습니다. 이 Receiver는 export된 읽기 전용 endpoint이므로 최종 승인 환경에서 공급자가 제공하는 sender 인증/권한 방식과 실제 패키지를 검증해야 합니다.

## 남은 공급사 연동

Gleo 최종 판단을 센서 변화마다 자동으로 요청하려면 별도의 호출 API·입력 계약·권한·지연/오프라인 정책이 필요합니다. 현재 공개 문서에서는 해당 계약이 확인되지 않아 임의 endpoint나 가짜 응답을 구현하지 않았습니다. 자동 안내는 로컬 후보 → 음성 파일 경로이며, Gleo의 최종 음성 답변은 위의 사용자 요청 흐름에 해당합니다.

## 공식 문서

- [Core Concepts](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts)
- [App Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability)
- [Callback Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback)
- [Android 예시](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples)
- [Schema 전달](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema)
