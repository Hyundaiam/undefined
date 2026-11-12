# 센서와 판단 범위

모든 신호는 기기·차종·권한별로 다릅니다. Vehicle SDK의 `CapabilityConfig.accessType`이 READ 또는 READ_WRITE인 항목만 읽습니다. 영역 목록과 지원 범위를 사용하며 제어 권한은 요청하지 않습니다.

| 입력 | 연결 | 현재 사용/제약 |
| --- | --- | --- |
| 탑승·안전벨트 | Vehicle Seat | 탑승이 확인되고 벨트 값도 있는 좌석만 비교 |
| 등받이 위치 | Vehicle Seat | 지원 범위와 운전자별 정차 시 등록 기준에 대한 상대 편차. 원시 숫자를 도 단위로 단정하지 않음 |
| 창문 위치 | Vehicle Window | 정차 시 직접 등록한 닫힘 위치와 비교. 터널 데이터 없이는 터널 안내 안 함 |
| 조향각 | Vehicle SteeringWheel | 원시 값 기록. 한 손/양손 여부, 곡률·권장 속도로 추정하지 않음 |
| 브레이크 조작 | Vehicle Brake | 원시 상태 기록. 어느 발인지, 긴급 회피인지 알 수 없음 |
| 방향지시등 | Vehicle TurnSignal | 원시 상태 기록. 차로 변경 센서 없이는 미사용 판정 안 함 |
| 속도 | 선택적 공개 AAOS CarPropertyManager | PERF_VEHICLE_SPEED, m/s → km/h. CAR_SPEED 런타임 권한 요청. 미지원·권한 거절 시 null |
| 기어 | 선택적 공개 AAOS CarPropertyManager | GEAR_SELECTION, CAR_POWERTRAIN. deprecated Pleos Gear API를 대체. P/D 등을 상수 이름으로 매핑 |
| 도로 제한 속도 | NaviHelper | onCurrentRoadSpeedLimit. 0/범위 밖 값과 30초 이상 오래된 값 제외 |
| 내비게이션 경로 | NaviHelper | 경로 시작/종료 상태. 음성 발화 여부를 뜻하지 않음 |
| 위치 | FusedLocation | 의존성·지원 여부 확인만. 위치 수집/저장 안 함 |
| 앞차 거리·차종 | 미연결 | 별도 ADAS 인지 계약 필요 |
| 손/발 위치·양발 여부 | 미연결 | 추가 실내 센서 필요. 조향각/페달 상태로 발·손 개수를 추측하지 않음 |
| 차선·보행자·날씨·터널·곡선 권장 속도 | 미연결 | 검증된 외부 인지/내비게이션 정보 필요 |
| 시동 종료·주차 완료 | 미연결 | 기어 값 하나로 시동 종료·주차 완료를 단정하지 않음 |

자동 후보는 제한 속도/실제 속도, 탑승 확인+벨트, 등록 기준 대비 좌석 편차, 연속 속도 변화 등 확인된 입력 조합에서만 생성됩니다. 미연결 신호를 요구하는 코치는 실행되지 않습니다. 30개 상황은 녹음·시연 목록이며 현재 자동 감지 수와 같지 않습니다.

## 시간·반복·개인화

- 속도는 3초, 제한 속도는 30초 이내 입력만 평가합니다. 누락 값은 정상으로 채우지 않습니다.
- 일반 신호 프레임은 오래되면 평가하지 않으며 서비스는 차량 속도를 매초 다시 조회합니다. Seat/Window 등의 on-change 입력은 해당 SDK의 유효한 현재 상태와 실패 콜백에 의존합니다. 차량 연결 중단·재연결 동작은 Pleos 런타임에서 추가 검증해야 합니다.
- 조건 유지 시간, 동일 상황 재발 간격(좋은 5분/주의 2분), 음성 간격(좋은 60초/주의 15초)을 둡니다.
- 속도 초과의 +3 km/h 필터는 센서 노이즈를 줄이는 개발 임계값이며 법적 허용 오차가 아닙니다.
- 급제동은 불가피한 회피가 아님을 확인한 입력이 있어야 주의 후보가 됩니다. 현재 실제 입력에서는 이를 알 수 없어 해당 평가를 하지 않습니다.
- 개인화는 운전자·입력 출처·습관별 누적과 이번/직전 주행 비교입니다. 모델 재학습이나 법규 위반 확정 판정은 구현하지 않았습니다.
- 좌석·창문 기준 등록은 최신 속도 0.5 km/h 이하와 P단 신호가 있는 종료 직후에만 허용합니다. 기준과 다른 자세를 ‘위험한 각도’로 단정하지 않습니다.

## 저장과 백그라운드

앱의 내부 Room DB에 운전자, 주행, 코칭 후보, 습관 통계, 최근 원시 관측, 녹음 연결을 저장합니다. 관측은 2초 간격이며 최근 5,000개를 유지합니다. 이름·캐릭터·보정값·통계는 운전자별로 구분합니다. 음성 팩은 캐릭터/상황별 공유입니다. 클라우드 업로드, 위치 경로, 카메라 영상 수집은 없습니다. Android 백업도 비활성화했습니다.

사용자가 화면에서 시작한 `specialUse` foreground service로 기록합니다. 지속 알림과 중지 액션이 있으며 부팅 시 자동 실행하지 않습니다. 강제 종료 후 미종료 주행은 마지막 heartbeat 시점에 끝난 것으로 복구합니다. Android 설정이나 제조사 정책에 따른 강제 중지까지 방지할 수 있는 구조는 아닙니다.

오디오 포커스를 잃으면 짧은 안내를 중지하고 뒤늦게 재개하지 않습니다. 내비게이션 음성의 발화 시작을 직접 알려주는 SDK는 연결되어 있지 않으므로 내비 앱과의 실제 우선순위 동작은 차량에서 확인해야 합니다.

## 근거

- [Vehicle Capability](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/entry)
- [Vehicle 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [공개 AAOS VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- [Foreground service 시작](https://developer.android.com/develop/background-work/services/fgs/launch)
