# 요구사항 요약 (SPEC v1.0)

원문: "XposedTeyes 설정 중심 확장 — 코딩 에이전트 구현 명세서" v1.0 (기준 커밋 `4c376ab`). 이 파일은 구현 추적용 요약이며, 세부 문구는 원문을 따른다.

## 최우선 원칙

- 실행 정책과 값은 **XposedTeyes 앱 설정 화면에서** 고른다. 인터뷰 값은 기본값일 뿐이다. 소스 수정·재빌드·패키지명 입력을 요구하지 않는다.
- 범위: ① 시작 앱 설정 → ② CarPlay DPI·화면 배율 → ③ BT 마이크 음질. ②③은 분석과 실제 제어 구현을 구분하며, 동작하지 않는 UI 를 완료로 보고하지 않는다.
- 상태 구분: `IMPLEMENTED` / `DEVICE_VERIFIED` / `BLOCKED` / `UNSUPPORTED` / `NOT_TESTED`.

## 요구사항 ID → 구현 위치

| ID | 내용 | 위치 |
|---|---|---|
| ST-01 | 앱 선택기 | `AppPickerActivity`, `SettingsActivity.setAppSummary/resolve`, `SystemUiStartupHook.Env.resolve` |
| ST-02 | 부팅/ACC 이벤트 | `StartupCoordinator.onHookInit/onBootCompleted/onAccOn/onAccOff`, `SystemUiStartupHook.install` |
| ST-03 | 지연·실행·중복 방지 | `StartupCoordinator` (Scheduler 주입, StateStore) |
| ST-04 | 성공·실패 정의 | `StartupStatus`, `LaunchOutcome`, `StartupCoordinator.fire` |
| ST-05 | 화면 정책·시스템 우선순위 | `ForegroundPolicy`, `Env.foregroundPackage/homePackage` |
| ST-06 | 분할화면·BT·기존 기능 통합 | `SplitLaunchActivity`, `LauncherService.ACTION_SPLIT`, `onBluetoothState`, `ConflictPolicy` |
| §6 | 설정 모델·공유·마이그레이션·복구 | `settings/*`, `Prefs`, `SettingsActivity.migrate`, `SettingsCodec` |
| CP-01~04 | CarPlay | BLOCKED — `docs/research/carplay.md`, `DiagHooks` |
| BT-01~05 | BT 마이크 | BLOCKED — `docs/research/bt-mic.md`, `BtMicDiagnostics`, `DiagHooks` |
| BUILD-01 | 빌드·CI | `build.gradle`, `gradle/wrapper`, `.github/workflows/build.yml`, `docs/BUILD.md` |

## 시작 앱 기본값 (신규 설치·기본값 복원)

| 설정 | 선택지 | 기본값 | 저장 키 |
|---|---|---|---|
| 자동 실행 | ON/OFF | OFF | `startupEnabled` |
| 실행 시점 | 완전 부팅 / ACC ON·절전 복귀 / 둘 다 | 완전 부팅만 | `startupTrigger` |
| 실행 방식 | 단일 / 분할화면 | 단일 | `startupMode` |
| 시작 앱 / 두 번째 앱 | 실행 가능한 앱 | 미선택 | `startupPrimary` / `startupSecondary` (`패키지/클래스`) |
| 실행 지연 | 0–60초 (0·3·5·10·15 바로 선택) | 5초 | `startupDelaySeconds` |
| 실패 재시도 | 0–3회 | 1회 (= 최대 2회 요청) | `startupRetryCount` |
| 재시도 간격 | 1–30초 | 5초 | `startupRetryDelaySeconds` |
| 실행 후 종료 정책 | 재실행 없음 / 비정상 종료만 / 제한 유지 | 재실행 없음 (다른 값은 BLOCKED) | `startupPostLaunchPolicy` |
| 복구 한도 / 대기 | 1–3회 / 5–60초 | 1회 / 5초 | `startupRecoveryLimit` / `startupRecoveryDelaySeconds` |
| 다른 앱이 전면일 때 | 전면 실행 / 홈 화면일 때만 | 전면 실행 | `startupForegroundPolicy` |
| BT 연결 시 실행 | ON/OFF, 앱, 지연 0–60초 | OFF, 3초 | `btLaunchEnabled`, `btLaunchTarget`, `btLaunchDelaySeconds` |
| 충돌 정책 | 시작 우선 / BT 우선 / 마지막 이벤트 | 시작 우선 | `btConflictPolicy` |
| 연결 해제 시 시작 앱 실행 | ON/OFF | OFF | `btLaunchStartupOnDisconnect` |
| 후방카메라·통화 | 시스템 기본 유지 | 유지 (설정 아님) | — |
| 신규 기능 전체 끄기 | ON/OFF | OFF | `newFeaturesDisabled` |

## 해석상 결정 (원문이 정하지 않은 부분)

| 항목 | 결정 | 근거 |
|---|---|---|
| 저장 형식 | 기존 평면 SharedPreferences 키 유지 (JSON 아님) | XSharedPreferences 호환, 원문 §6.1 "기존 저장 형식 강제 안 함" |
| 재시도 가능 실패 | `startActivity` 의 `ActivityNotFoundException`·`SecurityException` 은 영구 실패, 그 외 런타임 예외는 재시도 가능 | 영구 실패의 반복 방지 |
| 실행 확인 | 요청 2초 뒤 최상위 작업 패키지가 대상이면 `STARTED`, 아니면 `UNCONFIRMED`(재실행 없음) | §5.5 |
| BT 실행 재시도 | 시작 앱의 재시도 횟수·간격을 공유 | 별도 설정을 늘리지 않음 |
| 충돌 판정 범위 | 아직 실행 요청 전(대기·재시도 대기)인 작업끼리만 충돌 | 이미 요청된 실행은 되돌릴 수 없음 |
| "둘 다" 모드 첫 세션 | 부팅 후 첫 ACC ON(ACC OFF 이전)과 BOOT_COMPLETED 중 먼저 온 것 하나만 | §5.4 |
| 늦은 등록 | 이번 부팅에서 부팅 완료 전에 훅이 등록된 기록이 있을 때만 따라잡기 실행 | §5.3 |
| 3.0 사용자 마이그레이션 | `launchOnStartup` → ACC ON·분할화면·지연 1초·루트 자동 활성화 ON, BT 실행은 3.0 의 숨은 기본값(true) 반영 | "기존 앱 선택·트리거 보존" |
