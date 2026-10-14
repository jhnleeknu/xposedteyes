# 구현 계획과 진행 현황

기준: SPEC v1.0 (분석 기준 커밋 `4c376ab`). 작업 시작 시 `main` 은 기준 커밋과 같았다(차이 없음).

## 브랜치

SPEC §12 는 단계별 브랜치(PR-0, PR-1, …)를 권장하지만, 이 작업 세션은 지정된 단일 브랜치 `claude/focused-heisenberg-vhfqhv` 에만 푸시하도록 제한되어 있다. 대신 단계별로 **커밋을 분리**했다. 필요하면 각 커밋을 기준으로 PR 을 나눌 수 있다.

| 단계 | 상태 | 산출물 |
|---|---|---|
| PR-0 빌드·안전 기반 | IMPLEMENTED | Gradle Wrapper/AGP 고정, source set 매핑, CI, lint 기준선(기존 코드만), 설정 모델·검증·마이그레이션·가져오기/내보내기, 로컬 로그, 기능 상태 표 |
| PR-1 시작 앱 관리 | IMPLEMENTED (실기기 NOT_TESTED) | 앱 선택기, 실행 엔진(`StartupCoordinator`), SystemUI 연결, 분할화면·BT 통합, 설정 UI, 테스트 |
| PR-1 고급(종료 후 복구, 마지막 앱 복원) | BLOCKED | 정책 로직만 구현·테스트. 신호원 없음 → UI 비활성 + 사유 |
| PR-2A CarPlay 분석 | BLOCKED | `docs/research/carplay.md`, 읽기 전용 진단 로그 |
| PR-2B CarPlay 구현 | 미착수 (2A 차단) | — |
| PR-3A BT 경로 분석 | BLOCKED | `docs/research/bt-mic.md`, 진단 화면·로그 |
| PR-3B BT 개선 구현 | 미착수 (3A 차단) | — |

## 구조

```text
src/com/pijulius/xposedteyes/
  settings/   Keys, StartupSettings(타입·범위·기본값), SettingsMigration, SettingsCodec, Target, PrefsReader
  startup/    StartupCoordinator(순수 Java 정책), StartupEnv/Scheduler/StateStore(주입), SystemUiStartupHook(Android 연결)
  compat/     FeatureStatus
  logging/    LocalLog (1MiB 순환, 마스킹)
  diag/       DiagHooks (읽기 전용 분석 훅, 기본 OFF)
  SettingsActivity, AppPickerActivity, IntPickerPreference, SplitLaunchActivity, StatusReceiver, StatusStore,
  Prefs, AccessibilityHelper, BtMicDiagnostics
  XposedTeyes (기존 진입점 — SystemUI 블록만 새 엔진으로 교체)
test/         JVM 단위 테스트
```

## 3.0 대비 동작 변경

| 3.0 | 3.1 | 이유 |
|---|---|---|
| 첫 ACC ON 마다 `LauncherActivity` 로 `su` 실행, 접근성 서비스 목록을 이 서비스 하나로 **덮어씀** | 분할화면 실행 시점에 서비스가 꺼져 있고 사용자가 "필요 시 루트로 활성화"를 켠 경우에만, 기존 목록을 보존하며 활성화. 메인 스레드 차단 없음, 10초 타임아웃, 실패를 성공으로 반환하지 않음 | S4, §6.3 |
| `action.contains(".ACCON")` | 정확히 일치하는 4개 액션 | §5.3 |
| 프로세스 정적 boolean 으로 중복 방지 | 부팅 식별값·ACC 주기별 상태를 SystemUI 기기 보호 저장소에 기록 | §5.4 |
| 설정 변경 후 재부팅 필요 | 이벤트마다 `XSharedPreferences.reload()`, 변경 시 SystemUI 에 알림 | §6.2 |
| BT 상태 2 외 값은 모두 "해제" | 2=연결, 0=해제, 나머지는 무시(통화 중 실행 방지) + 로그 | §5.6 |
| BT 해제 시 `launchOnStartup` 이면 분할화면 재실행 | "연결 해제 시 시작 앱 실행" 옵션(기본 OFF, 3.0 사용자는 마이그레이션으로 ON) | §5.7 |
| `SettingsActivity.onPause()` 에서 `finish()` | 제거 | §6.3 |
| 패키지명 직접 입력(자동 실행용) | 앱 선택기. 분할화면 바로가기용 3개 입력은 그대로 유지 | ST-01 |

## 후속 작업 (추적)

1. **종료 후 복구 신호원**: system_server(Android Framework 범위)에서 AMS 의 앱 사망·크래시·강제 중지 경로를 훅해 SystemUI 로 전달(서명 권한 보호) → `StartupEnv.exitSignalAvailable()` 를 true 로. 정상 종료·크래시·강제 중지·백그라운드 전환 구분을 실기기로 검증한 뒤에만 UI 활성화.
2. **마지막 사용 앱 복원**: 화면/세션 식별 방법 조사 후 별도 설계.
3. **CarPlay**: research/carplay.md §4 자료 확보 후 2A 완료 → 2B.
4. **BT 마이크**: research/bt-mic.md §5 자료 확보 후 3A 완료 → 3B.
5. 실기기 테스트(TEST_PLAN.md) 결과로 COMPATIBILITY.md 의 가정 A-01~A-10 갱신.
