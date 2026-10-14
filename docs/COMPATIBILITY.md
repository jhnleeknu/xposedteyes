# 호환성 · 지원 상태

`IMPLEMENTED` 코드+자동화 테스트 / `DEVICE_VERIFIED` 실기기 확인 / `BLOCKED` 자료·환경 부족 / `NOT_TESTED` 미확인(지원과 같은 뜻 아님).

| 기능 | 코드 | 실기기 |
|---|---|---|
| 시작 앱 (단일 앱, 부팅/ACC, 지연·재시도·중복 방지, 홈 화면일 때만) | IMPLEMENTED | NOT_TESTED |
| BT 연결 시 실행, 시작 실행과의 충돌 정책 | IMPLEMENTED | NOT_TESTED |
| 앱 선택기 | IMPLEMENTED | NOT_TESTED (에뮬레이터 UI 확인만) |
| CarPlay UI 크기 (CarLink 2.22–2.25, 물리 크기 보고값 조정) | IMPLEMENTED | NOT_TESTED |
| 기존 Android Auto 해상도·DPI | CarPlay 에는 적용되지 않음 | — |
| CarLink 영상 진단 로그 (읽기 전용) | IMPLEMENTED | NOT_TESTED |
| BT 마이크 이득 제어 | BLOCKED (경로 미확인) | — |
| BT 마이크 경로 진단 화면·오디오 경로 진단 로그 (읽기 전용) | IMPLEMENTED | NOT_TESTED |

## 실기기에서 확인해야 하는 가정

| ID | 가정 | 실패 시 |
|---|---|---|
| A-01 | SystemUI 가 `BOOT_COMPLETED` 전에 훅을 등록한다 | 그 부팅은 실행하지 않음 (안전 측) |
| A-02 | SystemUI 가 `boot_count` 또는 `/proc/sys/kernel/random/boot_id` 를 읽을 수 있다 | 재시작 시 중복 방지·따라잡기 없음 |
| A-03 | `sys.boot_completed` 를 읽을 수 있다 | "부팅 완료"로 간주 → 콜드 부팅 실행이 항상 생략됨. 반드시 확인 |
| A-04 | ACC 액션 문자열이 3.0 과 같다 | ACC 모드 실행 없음 |
| A-05 | SystemUI 의 `startActivity` 가 백그라운드 실행 제한을 받지 않는다 | `UNCONFIRMED`, 반복 실행 없음 |
| A-06 | SystemUI 가 `getRunningTasks(1)` 로 최상위 작업을 본다 | `UNCONFIRMED` / 홈 화면 정책은 생략 |
| A-07 | SystemUI 가 `STATUS_BAR_SERVICE` 권한을 가져 상태 보고가 앱에 도착한다 | 앱의 *최근 실행 결과* 가 비어 있음 (LSPosed 로그에는 남음) |
| A-08 | LSPosed 에서 `XSharedPreferences.reload()` 가 최신 값을 읽는다 | 오래된 값 사용 가능 |
| A-09 | `BluetoothControl.updata` 2=연결, 0=해제 | LSPosed 로그 `BluetoothControl state N` 으로 확인 |
