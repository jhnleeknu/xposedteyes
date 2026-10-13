# 호환성

## 1. 확인한 실행 조합

| 기기 | Android | 펌웨어 | LSPosed | CarLink | 결과 |
|---|---|---|---|---|---|
| (없음) | — | — | — | — | 이번 변경은 실기기에서 실행하지 않았다 |

원래 README의 시험 환경은 TEYES CC3 2K / Android 10이다. 이를 모든 CC3·모든 펌웨어로 확대 해석하지 않는다.
실기기 검증 결과가 생기면 위 표에 기기·버전·APK SHA-256과 함께 추가한다.

## 2. 기능별 상태

| 기능 | 코드 | 실기기 | 의존하는 제조사/시스템 지점 | 지점이 없을 때 |
|---|---|---|---|---|
| 시작 앱 (단일) | IMPLEMENTED | NOT_TESTED | `com.android.systemui.SystemUIApplication.onCreate`, `ACTION_BOOT_COMPLETED` | 설치 실패를 LSPosed 로그에 남기고 기능 비활성 |
| ACC ON / 절전 복귀 | IMPLEMENTED | NOT_TESTED | 브로드캐스트 `com.fyt.boot.ACCON/ACCOFF`, `com.glsx.boot.ACCON/ACCOFF` (정확히 일치) | 해당 이벤트가 오지 않으면 `ACC_ON` 트리거는 동작하지 않음 |
| 시작 앱 (분할) | IMPLEMENTED | NOT_TESTED | XposedTeyes `LauncherService` 접근성 서비스, 시스템 분할화면 | 서비스가 꺼져 있으면 첫 번째 앱만 실행(토스트·로그) |
| BT 연결 시 실행 | IMPLEMENTED | NOT_TESTED | SystemUI `com.syu.view.BluetoothControl.updata(int)`; 2=연결, 0=해제 | 클래스가 없으면 BT 실행 비활성(로그). 다른 상태값은 무시 |
| 실행 결과 확인 | IMPLEMENTED | NOT_TESTED | `ActivityManager.getRunningTasks(1)` (SystemUI 권한) | 확인 불가 시 `UNCONFIRMED`, 재요청 안 함 |
| 상태 보고 → 설정 화면 | IMPLEMENTED | NOT_TESTED | SystemUI가 `STATUS_BAR_SERVICE` 권한 보유 | 보고가 표시되지 않음(기능에는 영향 없음) |
| 설정 변경 즉시 취소 | IMPLEMENTED | NOT_TESTED | SystemUI 동적 수신기 + 서명 권한 | 실행 시점 재확인으로 여전히 취소됨 |
| 실행 후 종료 복구 / 제한 유지 | **BLOCKED** | NOT_TESTED | 앱 비정상 종료 신호(미확보) | UI 비활성, 엔진은 절대 재실행하지 않음 |
| 마지막 사용 앱 복원 | **BLOCKED** | NOT_TESTED | 화면/세션 식별(미확보) | 제공하지 않음 |
| Android Auto 해상도·DPI (기존) | IMPLEMENTED(기존) | NOT_TESTED | `com.syu.carlink.auto.AutoFragment`, Google projection `VideoConfiguration` | 기존 동작 그대로 |
| CarPlay 화면 제어 | **BLOCKED** | NOT_TESTED | 미확인 (`docs/research/carplay.md`) | 조절기 비활성, 아무것도 바꾸지 않음 |
| BT 마이크 읽기 전용 진단 | IMPLEMENTED | NOT_TESTED | 공개 AudioManager API, `SystemProperties` 읽기 | 읽지 못하면 "(not readable)" 표시 |
| BT 마이크 제어 | **BLOCKED** | NOT_TESTED | 미확인 (`docs/research/bt-mic.md`) | 조절기 비활성, 어떤 값도 쓰지 않음 |

## 3. 고급 종료 정책이 BLOCKED인 이유

명세 5.1은 정상 종료·오류 종료·단순 백그라운드 전환을 신뢰성 있게 구분할 수 없으면 비정상 종료 복구를 활성화하지 말라고 한다.
현재 코드가 접근할 수 있는 신호는 다음뿐이다.

- SystemUI의 최상위 작업 조회: 앱이 화면에서 사라졌다는 것만 알 수 있다(홈 버튼·다른 앱 전환과 구분 불가).
- `ActivityManager.getHistoricalProcessExitReasons`: Android 11(API 30)부터 제공되어 Android 10 대상에서 쓸 수 없다.

다음 분석 대상: 모듈이 이미 `Android Framework`(system_server) 범위에서 동작하므로, `ActivityManagerService`의 크래시·ANR 처리 경로
(`handleApplicationCrashInner` 등, Android 10 소스 기준)를 훅해 "크래시로 인한 종료"만 SystemUI로 전달하는 방법.
사용자 강제 중지(`forceStopPackage`)는 별도 경로이므로 구분 가능하다. 실기기에서 세 경우(크래시·홈 버튼·강제 중지)를 구분해 확인하기 전에는
옵션을 켜지 않는다.

## 4. 런타임 안전장치

- 알 수 없는 클래스·메서드이면 해당 기능만 비활성화하고 SystemUI 원래 동작은 유지한다.
- 시작 앱 엔진은 같은 SystemUI 프로세스에서 오류가 5회 나면 스스로 멈추고 대기 작업을 취소한다.
- 새 훅은 SystemUI(기존 범위)에만 설치했다. 새 전역 훅은 없다.
