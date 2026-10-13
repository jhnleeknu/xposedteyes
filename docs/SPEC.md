# XposedTeyes 설정 중심 확장 — 요구사항 요약과 상태

원문: 「XposedTeyes 설정 중심 확장 — 코딩 에이전트 구현 명세서」 v1.0 (기준 커밋 `4c376ab`).
이 문서는 원문의 요구사항 ID·기본값·완료 조건을 저장소 안에서 추적하기 위한 요약이다. 해석이 갈리면 원문이 우선한다.

## 1. 범위와 순서

1. 시작 앱 설정 (`ST-*`) — 이번 변경에서 구현.
2. CarPlay DPI·화면 배율 (`CP-*`) — 분석(2A) 단계. 실제 제어(2B)는 **BLOCKED**.
3. BT 통화 마이크 (`BT-*`) — 분석(3A) 단계, 읽기 전용 진단만 구현. 실제 제어(3B)는 **BLOCKED**.

인터뷰에서 정한 값은 **기본값**이며 사용자는 XposedTeyes 설정 화면에서 바꿀 수 있다. 소스 수정·재빌드·패키지명 입력은 필요 없다.

## 2. 상태 용어

| 상태 | 의미 |
|---|---|
| `IMPLEMENTED` | 코드와 자동화 테스트 완료 |
| `DEVICE_VERIFIED` | 지정한 실기기·앱 버전 조합에서 확인 |
| `BLOCKED` | 구현 지점·자료·실행 환경이 없어 진행이 막힘 |
| `UNSUPPORTED` | 근거를 갖고 미지원 확인 |
| `NOT_TESTED` | 아직 확인하지 않음 (지원됨과 다름) |

## 3. 요구사항별 현재 상태

| ID | 요구사항 | 코드 | 실기기 | 근거 |
|---|---|---|---|---|
| BUILD-01 | 재현 가능한 빌드·CI | IMPLEMENTED | — | `settings.gradle`, `.github/workflows/build.yml` |
| ST-01 | 앱 선택기(아이콘·검색·alias·실행 불가 표시) | IMPLEMENTED | NOT_TESTED | `AppPickerActivity`, `AppCatalog`, `Targets` |
| ST-02 | 완전 부팅 / ACC ON 구분, 늦은 등록 처리 | IMPLEMENTED | NOT_TESTED | `LaunchCoordinator.init/onBootCompleted/onAccOn` |
| ST-03 | 지연·재시도·중복 방지·취소 | IMPLEMENTED | NOT_TESTED | `LaunchCoordinator`, `StartupRuntime` |
| ST-04 | 상태 정의(REQUEST_ACCEPTED/STARTED/UNCONFIRMED…) | IMPLEMENTED | NOT_TESTED | `LaunchStatus`, `SystemUiLauncher` |
| ST-05 | 화면 정책·시스템 우선순위 유지 | IMPLEMENTED | NOT_TESTED | `ForegroundPolicy`, `SystemUiLauncher` |
| ST-06 | 분할화면·BT 자동 실행·기존 기능 통합 | IMPLEMENTED | NOT_TESTED | `StartupSplitActivity`, `LauncherService`, BT 훅 |
| ST-고급 | 실행 후 종료 복구 / 제한 유지 / 마지막 앱 복원 | BLOCKED | NOT_TESTED | `docs/COMPATIBILITY.md` §3 |
| 6.x | 설정 모델·마이그레이션·백업/복원·전체 끄기 | IMPLEMENTED | NOT_TESTED | `core/settings/*`, `SettingsSync` |
| CP-01/02 | CarPlay 경로 분석 | 진행 중(BLOCKED: 자료 필요) | — | `docs/research/carplay.md` |
| CP-03/04 | CarPlay 제어·시험 적용·복원 | BLOCKED | NOT_TESTED | 위와 같음 |
| BT-01/02/03 | BT 경로 분석·읽기 전용 진단 | 진단 IMPLEMENTED, 경로 BLOCKED | NOT_TESTED | `docs/research/bt-mic.md`, `DiagnosticsActivity` |
| BT-04/05 | BT 이득·AGC·NS·AEC 제어, 비교 검증 | BLOCKED | NOT_TESTED | 위와 같음 |

실기기 검증은 한 건도 수행하지 않았다. 이 저장소의 어떤 항목도 `DEVICE_VERIFIED`가 아니다.

## 4. 시작 앱 기본값 (신규 설치·기본값 복원 기준)

| 설정 | 키 | 선택지 | 기본값 |
|---|---|---|---|
| 자동 실행 | `startupEnabled` | ON/OFF | OFF |
| 실행 시점 | `startupTrigger` | `COLD_BOOT` / `ACC_ON` / `BOTH` | `COLD_BOOT` |
| 실행 방식 | `startupMode` | `SINGLE` / `SPLIT` | `SINGLE` |
| 시작 앱 | `startupPrimary` | 실행 가능한 앱 (`package/activity`) | 미선택 |
| 두 번째 앱 | `startupSecondary` | 분할화면에서만 사용 | 미선택 |
| 실행 지연 | `startupDelayMs` | 0–60 s (0·3·5·10·15 프리셋) | 5 s |
| 재시도 횟수 | `startupRetryCount` | 0–3 | 1 (최초+추가 = 최대 2회 요청) |
| 재시도 간격 | `startupRetryDelayMs` | 1–30 s | 5 s |
| 실행 후 종료 정책 | `startupPostLaunchPolicy` | `NONE` / `RECOVER_ABNORMAL_EXIT` / `LIMITED_KEEP_ALIVE` | `NONE` (나머지는 BLOCKED, 비활성) |
| 복구 한도·대기 | `startupRecoveryLimit`, `startupRecoveryDelayMs` | 1–3회, 5–60 s | 1회, 5 s (비활성) |
| 다른 앱이 전면일 때 | `startupForegroundPolicy` | `ALWAYS` / `HOME_ONLY` | `ALWAYS` |
| BT 연결 시 앱 실행 | `btLaunchEnabled`, `btLaunchTarget`, `btLaunchDelayMs` | ON/OFF, 앱, 0–60 s | OFF, 미선택, 3 s |
| 충돌 정책 | `btConflictPolicy` | `STARTUP_FIRST` / `BLUETOOTH_FIRST` / `LAST_EVENT` | `STARTUP_FIRST` |
| 연결 해제 후 재실행 | `btLaunchOnDisconnect` | ON/OFF | OFF |
| 루트 접근성 재활성화 | `rootAccessibilityAssist` | ON/OFF (명시적 동의 대화상자) | OFF |
| 신규 기능 전체 끄기 | `newFeaturesDisabled` | ON/OFF | OFF |
| 후방카메라·통화 | — | 시스템 우선순위 유지 | 유지, 강제 덮기 없음 |

설정 스키마 버전은 `settingsSchemaVersion` (현재 1)에 기록한다. 원문 6.1의 JSON 예시는 백업 파일 형식(`docs/RECOVERY.md`)과 구조가 다르며, 원문도 형식을 강제하지 않는다.

## 5. 원문에서 내린 해석 (설계 결정)

- **`ACC_ON` 모드**: 부팅 직후 첫 ACC ON도 ACC ON 이벤트로 본다. 부팅 완료 전에 오면 부팅 완료까지 기다렸다가 예약한다(사용자 잠금 해제 전 실행 실패 방지).
- **`BOTH` 모드**: 같은 부팅의 첫 ACC ON은 부팅 세션의 중복 원인으로 합친다. 이후 ACC OFF → ON만 새 세션.
- **같은 ACC 전환의 여러 액션**(`com.fyt.boot.ACCON`, `com.glsx.boot.ACCON`)은 상태 전이로 중복 제거한다.
- **놓친 부팅 이벤트**: 이 부팅을 이전 SystemUI 인스턴스가 관찰해 `SCHEDULED`로 기록한 경우에만 재개한다. 기록이 없으면(모듈을 실행 중 활성화한 경우 등) 실행하지 않는다.
- **BT 상태값**: `BluetoothControl.updata(int)`의 2=연결, 0=해제만 사용한다. 그 밖의 값(통화 중 등으로 추정)은 무시하고 로그만 남긴다.
- **BT 연결 실행에는 재시도를 적용하지 않는다**(이전 버전과 동일). 연결 해제 후 재실행은 시작 앱의 대상·모드·재시도 설정과 BT 지연을 사용한다.
- **충돌**: 두 세션이 동시에 대기 중(예약·재시도·확인 대기)일 때만 충돌로 본다.
