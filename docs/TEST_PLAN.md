# 시험 계획과 결과

## 자동화 테스트 (JVM)

실행: `./gradlew testDebugUnitTest` — 이 브랜치에서 61개 통과(실패 0). CI 에서도 실행.

시간은 가짜 스케줄러(`test/.../startup/FakeScheduler.java`)로 제어하므로 예약 시점을 ms 단위로 검증한다. Android/Xposed 의존 부분(`SystemUiStartupHook`, UI)은 JVM 테스트 대상이 아니다.

| ID | 테스트 (`StartupCoordinatorTest` 등) | 상태 |
|---|---|---|
| T-ST-01 | `ST01_disabledByDefault_neverLaunches` | 통과 |
| T-ST-02 | `ST02_coldBoot_launchesOnceAfterFiveSeconds` (4999ms 에는 미실행, 5000ms 정확히 1회) | 통과 |
| T-ST-03 | `ST03_defaultMode_ignoresAccOnAndResume` | 통과 |
| T-ST-04 | `ST04_repeatedBootBroadcast_keepsSingleTimer` | 통과 |
| T-ST-05 | `ST05_systemUiRestartAfterHandling_...`, `ST05b_..._catchesUpOnce`, `ST05c_moduleEnabledOnAlreadyBootedDevice_...`, `ST05d_unknownBootId_noCatchUp`, `ST05e_newBoot_resetsState` | 통과 |
| T-ST-06 | `ST06_retryableFailure_retriesOnceAfterFiveSeconds` | 통과 |
| T-ST-07 | `ST07_...finalFailure`, `ST07b_retryCountZeroAndThree`, `ST07c_permanentFailure_notRetried` | 통과 |
| T-ST-08 | `ST08_exitAfterStart_defaultPolicyNeverRelaunches` | 통과 |
| T-ST-09 | `ST09_otherAppInFront_...` (요청만, 종료·오버레이 없음 — 엔진은 실행 요청 외 동작이 없음) | 통과 |
| T-ST-10 | `ST10_targetAlreadyInFront_noDuplicateLaunch` | 통과 |
| T-ST-11 | `ST11_disableWhilePending_...`, `ST11b_killSwitch...`, `ST11c_missedSettingsBroadcast_...` | 통과 |
| T-ST-12 | `ST12_changeTargetWhilePending_...`, `ST12b_settingsChangeAloneNeverLaunches` | 통과 |
| T-ST-13 | `ST13_invalidTarget_noFallbackNoRetry` | 통과 |
| T-ST-14 | `ST14_accOffWhilePending_cancelledAndNotRevived` | 통과 |
| T-ST-15 | `targetParsing`, `packageOnlyTarget_resolvesWithinSamePackage` (Target 파싱·같은 패키지 내 해석). 실제 PackageManager 의 alias·동명 앱 처리는 실기기 항목 | 부분(로직만) |
| T-ST-16 | `StartupCoordinatorTest` 의 프로세스 재생성 시나리오 + `StartupSettings.from` 이 매번 저장값을 다시 읽음. 실제 XSharedPreferences 재로딩은 A-08(실기기) | 부분(로직만) |
| T-ST-17 | `outOfRangeValues_useDefaultAndAreListed`, `boundaries`, `SettingsCodecTest.*` | 통과 |
| T-ST-18 | `ST18_bothMode_initialAccOnAndBootAreOneSession`, `ST18b_...`, `ST18c_accMode_...` | 통과 |
| T-ST-19 | `ST19_bluetoothTransitionsOnly`, `ST19b_disconnectDoesNotLaunchByDefault` | 통과 |
| T-ST-20 | `ST20_conflict_startupFirst`, `ST20b_conflict_bluetoothFirst`, `ST20c_conflict_lastEvent` | 통과 |
| T-ST-21 | `ST21_acceptedButNotConfirmed_notRepeated` | 통과 |
| T-ST-22 | `migrationIsIdempotentAndKeepsLegacyAndUserValues`, `AccessibilityHelperTest.*` (기존 접근성 목록 보존) | 통과 |
| T-ST-23 | `ST23_postLaunchRecovery_disabledWithoutExitSignal` | 통과 |
| (추가) | 홈 화면 정책, 분할화면 검증, 고급 복구 한도·강제 중지 비복구, 반복 오류 자동 정지, 로그 용량·마스킹 | 통과 |

변경 검증: 재시도 조건(`<=`→`<`)과 BOTH 모드 중복 판정을 일부러 망가뜨렸을 때 해당 테스트가 실패하는 것을 확인했다(뮤테이션 확인 2건).

## 에뮬레이터 스모크 테스트 (Android 9 / API 28, x86_64, 1280×720 가로, LSPosed 없음)

실기기 검증이 아니다. 훅(SystemUI·CarLink)은 이 환경에서 실행되지 않으므로 앱 UI·설정 저장·앱 쪽 실행 경로만 확인했다.

| 확인 항목 | 결과 |
|---|---|
| 설정 화면·중첩 화면(시작 앱/CarPlay/BT 마이크/고급) 표시, 기본값이 SPEC 표와 일치 | 통과 |
| 앱 선택기: 시스템 앱 목록·아이콘·검색 → 정확한 컴포넌트(`패키지/클래스`) 저장, 요약에 아이콘·이름 표시 | 통과 |
| 지연 선택기: 0/3/5/10/15 프리셋, 정수(int)로 저장 | 통과 |
| 지금 실행(단일 앱) → 선택 앱이 전면 | 통과 |
| 3.0 형식 설정 파일로 시작 → 마이그레이션 값·알림·`settings_backup_pre_v1.json` 생성, 패키지명만 있는 값이 런처 Activity 로 해석 | 통과 |
| 분할화면 지금 실행 + 접근성 꺼짐 + 루트 없음 → 루트 실패 기록 후 첫 번째 앱만 실행 | 통과 (에뮬레이터에서 발견한 누락을 수정한 뒤) |
| LSPosed 없는 상태에서 "훅과의 설정 공유: 비활성" 표시 | 통과 (에뮬레이터에서 발견한 오표시를 수정한 뒤) |
| 한국어 로캘 표시 | 통과 |
| 크래시 로그(`logcat -b crash`) | 없음 |

## 실기기 테스트 — 모두 NOT_TESTED

이 작업 환경에는 CC3 실기기가 없다. 자동화 테스트 통과로 아래 항목을 대체하지 않는다. 정차 상태에서 진행한다.

| ID | 절차 요약 | 기록할 것 |
|---|---|---|
| T-DEV-01 | 기본값(완전 부팅만, 5초)으로 콜드 부팅 1회, 절전→ACC ON 1회. 이어서 "둘 다" 모드 반복 | LSPosed 로그 `XposedTeyes startup:` 줄, 앱 *최근 실행 결과*, 부팅 완료 후 실제 실행까지 시간 |
| T-DEV-02 | 시작 지연 중 후진 기어 / 수신 통화, 실행 직후 후진 / 통화 | 후방카메라·통화 화면이 가려지지 않는지. 가려지면 출시 차단 회귀 |
| T-DEV-03 | 단일 앱 / 분할화면(접근성 켬·끔) / 앱 삭제 후 | 선택 앱과 일치, 접근성 꺼짐 시 첫 앱만 + 안내 |
| T-DEV-04 | BT 연결·통화·해제 반복 | `BluetoothControl state N` 값 목록(A-09), 통화 중 실행이 없는지 |
| T-DEV-05 | 설정 변경 직후(대기 중) OFF/앱 변경 | 실행 취소 여부(A-10) |
| T-CP-01~05 | docs/research/carplay.md 의 분석 후 정의 | BLOCKED |
| T-BT-01~05 | docs/research/bt-mic.md 의 분석 후 정의 | BLOCKED |
| T-REC-01 | 신규 기능 전체 끄기 / 기본값 복원 / 가져오기 거부 | 대기 작업 취소, 원래 동작 |
| T-PRIV-01 | 로그 내보내기 파일, 설정 내보내기 파일, CI 산출물 검사 | 식별번호·전화번호·음성·서명 키·제조사 APK 없음 |
