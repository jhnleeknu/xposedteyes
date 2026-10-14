# 호환성 · 지원 상태

상태 정의(SPEC §3): `IMPLEMENTED` 코드+자동화 테스트 완료 / `DEVICE_VERIFIED` 지정 기기·버전에서 확인 / `BLOCKED` 구현 지점·자료·환경 부족 / `UNSUPPORTED` 근거로 미지원 확인 / `NOT_TESTED` 미확인(지원과 같은 뜻이 아님).

앱의 *고급/진단 > 기능별 상태* 화면은 `src/com/pijulius/xposedteyes/compat/FeatureStatus.java` 의 표를 보여 준다. 이 문서와 함께 갱신한다.

## 기능별 상태 (이 브랜치 기준)

| 기능 | 요구 ID | 코드 | 실기기 | 비고 |
|---|---|---|---|---|
| 앱 선택기 (시스템+사용자 앱, 아이콘·검색·정확한 컴포넌트) | ST-01 | IMPLEMENTED | NOT_TESTED | 에뮬레이터(API 28)에서 UI 동작 확인 (실기기 아님) |
| 부팅/ACC 이벤트 구분, 늦은 등록 처리 | ST-02 | IMPLEMENTED | NOT_TESTED | |
| 지연·재시도·중복 방지·취소 | ST-03 | IMPLEMENTED | NOT_TESTED | |
| 결과 상태(STARTED/UNCONFIRMED 등) | ST-04 | IMPLEMENTED | NOT_TESTED | 전면 확인은 `getRunningTasks(1)` |
| 다른 앱 전면 정책, 홈 화면일 때만 | ST-05 | IMPLEMENTED | NOT_TESTED | 후방카메라·통화 우선순위는 변경하지 않음 |
| 분할화면 실행, BT 연결 실행, 충돌 정책 | ST-06 | IMPLEMENTED | NOT_TESTED | 분할화면은 기존 LauncherService(접근성) 재사용 |
| 실행 후 종료 복구(비정상 종료만/제한 유지) | §5.1 고급 | **BLOCKED** | NOT_TESTED | 정책 로직·한도는 구현·테스트됨. 비정상 종료/정상 종료/강제 중지를 구분하는 신호원이 없어 UI 비활성. 후속: system_server(AMS) 훅으로 신호원 구현·검증 |
| 마지막 사용 앱 복원 | §5.1 고급 | **BLOCKED** | NOT_TESTED | 화면/세션 식별 방법 미검증. 미구현 |
| 설정 마이그레이션·백업·가져오기/내보내기·전체 끄기 | §6 | IMPLEMENTED | NOT_TESTED | |
| CarPlay 실제 밀도/레이아웃 | CP-01, CP-03 | **BLOCKED** | NOT_TESTED | docs/research/carplay.md |
| CarPlay 영상 배율·여백·위치, 시험 적용 | CP-03, CP-04 | **BLOCKED** | NOT_TESTED | 동일 |
| CarLink 영상 형식 진단 로그(읽기 전용) | CP-02 | IMPLEMENTED | NOT_TESTED | 분석 도구 |
| BT 마이크 이득/AGC/NS/AEC 제어 | BT-04 | **BLOCKED** | NOT_TESTED | docs/research/bt-mic.md |
| BT 마이크 경로 진단(앱 내 읽기 전용) | BT-03 | IMPLEMENTED | NOT_TESTED | 녹음·입력 점유 없음 |
| 오디오 경로 진단 로그(읽기 전용 훅) | BT-02 | IMPLEMENTED | NOT_TESTED | 분석 도구 |
| Android Auto 해상도·여백·DPI (3.0 기존) | — | 기존 기능 | 3.0 기준 | 이름만 "Android Auto DPI" 로 명확화. CarPlay 지원으로 표시하지 않음 |

## 확인한 런타임 조합

| 조합 | 결과 |
|---|---|
| JVM 단위 테스트 (OpenJDK 21) | 통과 (docs/TEST_PLAN.md) |
| Android 9 에뮬레이터(API 28), LSPosed 없음 | 앱 UI·설정·앱 쪽 실행 경로 스모크 통과. 훅 미실행 |
| 실제 TEYES CC3 + LSPosed | **확인하지 않음** — 이 작업 환경에 실기기 없음 |

## 실기기에서 검증해야 하는 가정

코드가 의존하지만 CC3 에서 아직 확인하지 않은 사항. 각 항목은 실패 시 동작도 적었다.

| ID | 가정 | 실패 시 |
|---|---|---|
| A-01 | SystemUI 가 `ACTION_BOOT_COMPLETED` 를 받기 전에 훅이 등록된다 (SystemUI 는 부팅 중 시작) | 첫 등록이 부팅 완료 후면 그 부팅은 `SKIPPED_LATE_REGISTRATION` (안전 측) |
| A-02 | `Settings.Global boot_count` 또는 `/proc/sys/kernel/random/boot_id` 를 SystemUI 가 읽을 수 있다 | 둘 다 실패 시 메모리 내 중복 방지만, 늦은 등록 복구 없음 |
| A-03 | `sys.boot_completed` 를 리플렉션으로 읽을 수 있다 | 실패 시 "부팅 완료됨"으로 간주 → 그 부팅은 실행하지 않음 (안전 측). 이 경우 콜드 부팅 실행이 항상 생략되므로 실기기에서 반드시 확인 |
| A-04 | ACC 액션 문자열은 3.0 이 쓰던 `com.fyt.boot.ACCON/ACCOFF`, `com.glsx.boot.ACCON/ACCOFF` 와 정확히 일치한다 | 다른 문자열이면 ACC 모드 실행 없음. 추측한 액션은 추가하지 않음 |
| A-05 | SystemUI 의 `startActivity` 가 백그라운드 실행 제한을 받지 않는다 (SystemUI 는 `START_ACTIVITIES_FROM_BACKGROUND` 보유 예상) | 요청이 조용히 거절되면 `UNCONFIRMED` 로 표시, 반복 실행 안 함 |
| A-06 | SystemUI 가 `getRunningTasks(1)` 로 실제 최상위 작업을 본다 (`REAL_GET_TASKS`) | 못 보면 `UNCONFIRMED`, 홈 화면일 때만 정책은 `SKIPPED_FOREGROUND_UNKNOWN` |
| A-07 | SystemUI 가 `STATUS_BAR_SERVICE` 를 보유해 상태 수신기·`SplitLaunchActivity` 호출 권한을 통과한다 | 실패 시 상태 보고가 앱에 안 보임(LSPosed 로그에는 남음) / 분할화면 실행 실패 → `REQUEST_FAILED` |
| A-08 | LSPosed new-XSharedPreferences 에서 `reload()` 가 앱의 최신 저장값을 읽는다 | 오래된 값 사용 가능. 앱의 *기능별 상태 > 훅과의 설정 공유* 와 실행 결과로 확인 |
| A-09 | `com.syu.view.BluetoothControl.updata(int)` 의 2=연결, 0=해제. 통화 중 다른 값이 올 수 있다 | 0/2 외 값은 무시하고 로그만 남김(통화 중 실행 방지). 실제 값은 LSPosed 로그 `BluetoothControl state N` 으로 확인 |
| A-10 | 설정 변경 브로드캐스트(서명 권한)가 SystemUI 수신기에 도달한다 | 도달하지 않아도 실행 시점에 설정을 다시 읽어 취소 판단(이중 안전장치) |
