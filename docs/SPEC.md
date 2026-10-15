# 현재 범위 (3.1, 단순화 후)

SPEC v1.0 에서 출발했지만 사용자 결정으로 범위를 줄였다.

| 항목 | 결정 |
|---|---|
| 분할화면 자동 실행 | 제거. 시작 앱은 단일 앱만. (3.0 의 수동 분할화면 바로가기는 그대로) |
| 설정 마이그레이션·백업·가져오기/내보내기·신규 기능 전체 끄기·로그 화면·기능 상태 화면 | 제거. 옵션을 고르면 바로 저장된다 |
| 실행 후 종료 복구 / 마지막 앱 복원 | 제거 |
| CarPlay | *CarPlay* 화면: UI 크기(기본값 대비 %), 통화 마이크 음량·잡음 제거(루트). 기존 Android Auto 설정은 CarPlay 에 적용되지 않는다 (docs/research/carplay.md) |
| 일반 BT 통화 마이크 | 진단(읽기 전용)만. com.syu.bt 분석 필요 (docs/research/bt-mic.md) |

3.0 의 "시작 시 음악·내비 실행"(`launchOnStartup`)과 "블루투스 연결 시 실행"(`launchOnBluetooth`) 설정은 자동으로 옮기지 않는다. 업데이트 후 *시작 앱* 화면에서 다시 고른다.

## 시작 앱 설정 (기본값)

| 설정 | 선택지 | 기본값 | 저장 키 |
|---|---|---|---|
| 자동 실행 | ON/OFF | OFF | `startupEnabled` |
| 실행 시점 | 완전 부팅 / ACC ON·절전 복귀 / 둘 다 | 완전 부팅만 | `startupTrigger` |
| 시작 앱 | 앱 선택기 | 미선택 | `startupPrimary` (`패키지/클래스`) |
| 실행 지연 | 0–60초 (0·3·5·10·15 바로 선택) | 5초 | `startupDelaySeconds` |
| 실패 재시도 | 0–3회 | 1회 (= 최대 2회 요청) | `startupRetryCount` |
| 재시도 간격 | 1–30초 | 5초 | `startupRetryDelaySeconds` |
| 다른 앱이 전면일 때 | 전면 실행 / 홈 화면일 때만 | 전면 실행 | `startupForegroundPolicy` |
| BT 연결 시 실행 | ON/OFF, 앱, 지연 0–60초 | OFF, 3초 | `btLaunchEnabled`, `btLaunchTarget`, `btLaunchDelaySeconds` |
| 시작·BT 실행이 겹칠 때 | 시작 우선 / BT 우선 / 마지막 이벤트 | 시작 우선 | `btConflictPolicy` |

변경은 다음 해당 이벤트부터 적용된다. 자동 실행이나 BT 실행을 끄거나 앱·값을 바꾸면 대기 중인 실행은 취소된다. 설정을 바꾸는 것만으로 앱이 열리지는 않는다. 후방카메라·통화 화면은 시스템 우선순위를 그대로 따른다.

## 동작 규칙

- 완전 부팅: `ACTION_BOOT_COMPLETED`, 부팅당 1회. SystemUI 가 재시작돼도 같은 부팅에서 다시 실행하지 않는다. 이미 부팅된 기기에 모듈을 켠 것만으로는 실행하지 않는다.
- ACC: `com.fyt.boot.ACCON/ACCOFF`, `com.glsx.boot.ACCON/ACCOFF` 정확히 일치. "둘 다" 모드에서 부팅 직후의 첫 ACC ON 과 부팅 완료는 한 번으로 친다. ACC OFF 는 대기 중인 실행을 취소한다.
- 실행 확인: 요청 2초 뒤 최상위 앱이 대상이면 `STARTED`, 아니면 `UNCONFIRMED`(다시 실행하지 않음). 이미 전면이면 실행하지 않는다.
- 재시도: `ActivityNotFoundException`·`SecurityException` 은 재시도하지 않고, 그 외 실행 오류만 설정 횟수만큼 재시도한다. 앱이 실행된 뒤 종료되면 다시 실행하지 않는다.
- BT: `BluetoothControl.updata` 상태 2=연결, 0=해제, 다른 값(통화 중 등)은 무시.
