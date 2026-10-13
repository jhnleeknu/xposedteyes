# 구현 계획과 구조

## 1. 단계와 브랜치

명세 12절은 단계별 브랜치(`chore/build-and-safety-foundation`, `feat/startup-settings`, `research/*`, `feat/*`)를 권장하지만,
이 작업 세션은 지정된 단일 브랜치 `claude/zealous-johnson-u4roc8`에만 푸시할 수 있다. 그래서 다음처럼 커밋 단위로 나눴다.

| 명세 PR | 내용 | 이 브랜치에서의 상태 |
|---|---|---|
| PR-0 | 빌드·CI·설정 기반·공통 복구·기초 테스트 | 포함 |
| PR-1 | 시작 앱 UI·실행 엔진·마이그레이션·테스트 | 포함 |
| PR-2A | CarPlay 분석 보고 | `docs/research/carplay.md` (BLOCKED: 자료 필요), 읽기 전용 CarLink 정보 화면 |
| PR-2B | CarPlay 제어 | 미착수 (2A 차단) |
| PR-3A | BT 경로 분석·읽기 전용 진단 | `docs/research/bt-mic.md`, 읽기 전용 진단 화면 |
| PR-3B | BT 제어 | 미착수 (3A 차단) |

리뷰어가 원하면 PR-0/PR-1과 문서 커밋을 별도 PR로 나눌 수 있다.

## 2. 구조

```text
core/  (순수 Java 8, Android·Xposed 의존 없음, JVM 단위 테스트)
  settings/   Keys, AppSettings(읽기·검증·레거시 호환), Migration, SettingsTransfer(가져오기 화이트리스트),
              LaunchTarget, AccessibilityServices(목록 병합), Limits
  startup/    LaunchCoordinator(이벤트·타이머·재시도·중복 방지·충돌), Ports(주입 인터페이스), LaunchStatus…
  apps/       AppCatalog(정렬·중복 제거·검색·alias)
  compat/     Feature, FeatureStatus(기능별 상태)
  logging/    RingLog(1 MiB 순환), LogSanitizer(전화번호·MAC·IMEI·이메일 마스킹)

src/com/pijulius/xposedteyes/  (Android 앱 + Xposed 진입점, 기존 레이아웃 유지)
  XposedTeyes            기존 훅. SystemUI onCreate → StartupRuntime.install, BT 훅 → StartupRuntime
  startup/StartupRuntime SystemUI 안의 HandlerThread, 브로드캐스트 수신, 오류 한도(5회)
  startup/SystemUiLauncher  Ports.Launcher 구현 (일반 startActivity, 최상위 작업 확인)
  startup/Targets        LaunchTarget → 실행 Intent (정확한 컴포넌트, 모호하면 실행 불가)
  StartupSplitActivity   분할화면 실행(입력 없는 exported Activity) → LauncherService
  SettingsActivity       설정 화면, 마이그레이션, 내보내기/가져오기, 기본값 복원
  settings/*             AppPickerPreference/Activity, IntPickerPreference, SettingsSync
  diag/*                 DiagnosticsActivity(호환성·로그·CarLink·BT 진단), StatusReceiver, LocalLog
  RootAccessibility      동의 기반 su 재활성화(목록 병합, 10 s 타임아웃, 실패 코드 반환)
```

## 3. 프로세스 간 흐름

```text
[설정 앱]                                  [SystemUI (Xposed 훅)]
 SharedPreferences(MODE_WORLD_READABLE*)  ──► XSharedPreferences.reload() 이벤트마다
 SETTINGS_CHANGED 브로드캐스트 ───────────► StartupRuntime (signature 권한 필요)
                                           BOOT_COMPLETED / ACC ON·OFF / BT updata
                                             → LaunchCoordinator (단일 스레드)
                                             → SystemUiLauncher.startActivity
 diag.StatusReceiver ◄──── LAUNCH_STATUS ─ (수신기는 STATUS_BAR_SERVICE 보유 발신자만 허용)
 LocalLog (앱 내부 1 MiB)
```
\* LSPosed `xposedsharedprefs`가 허용할 때만. 그렇지 않으면 MODE_PRIVATE로 대체(훅도 비활성 상태).

- 실행 상태(부팅 키, 부팅 세션 단계, 첫 ACC 처리 여부)는 SystemUI의 **device protected storage**에 `commit()`으로 기록한다.
  SystemUI가 재시작해도 같은 부팅에서 중복 실행하지 않는다. 부팅 키(`Settings.Global.BOOT_COUNT` 등)는 기기 밖으로 나가지 않는다.
- 타이머는 `Handler.postDelayed`(uptimeMillis, 단조 시간)를 사용한다. 실행 시점에 설정을 다시 읽어 OFF·변경이면 취소한다.
- SystemUI 메인 스레드에서는 디스크 I/O를 하지 않는다(전용 HandlerThread).

## 4. 빌드 도구 선택

| 항목 | 값 | 근거 |
|---|---|---|
| Gradle | 8.14.3 (wrapper, SHA-256 고정) | AGP 8.7 최소 요구(8.9) 이상 |
| AGP | 8.7.3 | JDK 17 요구, compileSdk 28 지원 |
| JDK | 17 (CI) | AGP 8.x 요구 사항 |
| compileSdk / minSdk / targetSdk | 28 / 28 / 28 | 원래 `project.properties`·매니페스트 값 유지 |
| Xposed API | `lib/api-82.jar`, `compileOnly` | APK에 포함하지 않음 |

- AGP 8부터 매니페스트 `package` 속성 대신 `namespace`를 쓴다. 그래서 `AndroidManifest.xml`의 `package`·`versionCode`·`uses-sdk`를
  `app/build.gradle`로 옮겼다(값은 동일, versionCode만 30 → 31, versionName 3.1).
- min/target SDK는 바꾸지 않았다. 백그라운드 Activity 실행 제한(Android 10)은 SystemUI 프로세스에서 실행 요청을 보내는 기존 구조로 대응한다.
- 이 개발 환경에서는 Google Maven·Android SDK 다운로드가 차단되어 AGP 빌드를 로컬에서 실행하지 못했다.
  로컬에서는 `./gradlew -PcoreOnly :core:test`와 `tools/local-compile-check.sh`(aapt + javac, Robolectric android-all 9 사용)로 확인했고,
  APK·lint는 GitHub Actions 결과를 기준으로 한다.

## 5. 남은 일

- 실기기 시험 (`docs/TEST_PLAN.md` §2) — 모두 NOT_TESTED.
- 실행 후 종료 복구: system_server의 앱 크래시 경로(`ActivityManagerService` crash 처리)를 훅해 SystemUI에 신호를 전달하는 방안을 분석해야 한다.
  홈 버튼·사용자 강제 중지와 구분할 수 있음을 확인하기 전에는 활성화하지 않는다.
- CarPlay / BT 마이크: 각 research 문서의 "필요한 자료" 확보 후 분석 계속.
