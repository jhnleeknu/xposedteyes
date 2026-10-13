# 시험 계획과 결과

## 1. 자동화 테스트 (JVM, `./gradlew :core:test`)

최근 결과: 로컬 59개 통과(0 실패). CI 실행 38429870368(커밋 `0a88963`)에서 core 테스트·lint·debug APK 빌드 성공,
APK SHA-256 `7b51940f645473d51f552f449f7c95d1ea23e23b7432b2fe9968b9fa4b7e0c7a` (CI debug 키 서명, 실기기 미설치).

로컬(이 변경 작성 환경, JDK 21, `-PcoreOnly`)과 CI에서 실행한다. 결과는 CI 산출물 `core/build/reports/tests/test/`에 있다.

| ID | 테스트 (`core/src/test/...`) |
|---|---|
| T-ST-01 | `LaunchCoordinatorTest.tSt01_freshInstallLaunchesNothing` |
| T-ST-02 | `tSt02_singleAppFiveSecondsAfterBoot` (5 s 이전 실행 없음, 정확히 t0+5 s) |
| T-ST-03 | `tSt03_defaultModeIgnoresAccOnAndResume` |
| T-ST-04 | `tSt04_repeatedBootBroadcastKeepsOneTimer` |
| T-ST-05 | `tSt05_systemUiRestartAfterLaunchDoesNotRelaunch`, `tSt05b_restartWhileWaitingResumesOnce`, `moduleActivatedOnRunningSystemDoesNotLaunch` |
| T-ST-06 | `tSt06_retryableFailureRetriesOnceAfterFiveSeconds`, `retryCountZeroAndThreeAreHonoured`, `permanentFailureIsNotRetried` |
| T-ST-07 | `tSt07_retryAlsoFailsThenStop` |
| T-ST-08 | `tSt08_appExitAfterStartIsNotRelaunched`, `postLaunchPolicyOtherThanNoneStillNeverRelaunches` |
| T-ST-09 | `tSt09_otherAppInFrontStillRequestsOnce` (종료·오버레이 경로 자체가 없음은 코드 리뷰 항목) |
| T-ST-10 | `tSt10_targetAlreadyInFrontIsNotLaunchedAgain` |
| T-ST-11 | `tSt11_disablingWhileWaitingCancelsTimerAndRetries`, `disableIsHonouredAtFireTimeEvenWithoutNotification` |
| T-ST-12 | `tSt12_changingTargetOrPolicyCancelsAndDoesNotStartNewTimer`, `unrelatedSettingChangeKeepsTimer` |
| T-ST-13 | `tSt13_invalidTargetIsNotReplacedOrRetried` |
| T-ST-14 | `tSt14_accOffWhileWaitingIsNotRevivedOnResume` |
| T-ST-15 | `AppCatalogTest.*` (정렬·중복 제거·동명 앱·검색·alias·모호한 패키지 미선택) |
| T-ST-16 | `AppSettingsTest.tSt16_storedValuesRoundTripInBothRepresentations` |
| T-ST-17 | `AppSettingsTest.tSt17_*`, `SettingsTransferTest.tSt17_*` |
| T-ST-18 | `tSt18_bothModeMergesInitialAccOnAndOnlyRealResumesLaunch`, `bothModeBootFirstThenInitialAccOn`, `accOnlyModeWaitsForBootThenLaunchesOnEveryResume` |
| T-ST-19 | `tSt19_bluetoothTransitionsOnly`, `bluetoothDisconnectDoesNothingByDefault`, `bluetoothDisconnectRelaunchesStartupWhenEnabled`, `bluetoothSwitchOffCancelsPendingBluetoothLaunch` |
| T-ST-20 | `tSt20_conflictStartupFirst`, `tSt20_conflictBluetoothFirst`, `tSt20_conflictLastEvent` |
| T-ST-21 | `tSt21_acceptedButNotVerifiedIsUnconfirmedAndNotRepeated` |
| T-ST-22 | `MigrationTest.tSt22_migrationIsIdempotent`, `tSt22_accessibilityListIsPreserved` |
| T-ST-23 | `FeatureStatusTest.tSt23_unverifiedAdvancedFeaturesAreNotControllable` |
| T-PRIV-01 (일부) | `RingLogTest.tPriv01_sensitiveValuesAreMasked`, `sizeStaysBounded` |
| 레거시 | `legacyUnmigratedSettingsKeepPreviousBehaviour`, `AppSettingsTest.legacyReadReproducesPreviousBehaviour` |

자동화 테스트는 순수 정책 코드만 검증한다. 실제 `startActivity`, 브로드캐스트, XSharedPreferences, 접근성 서비스 동작은 아래 실기기 시험 대상이다.

## 2. 실기기 시험 — 모두 `NOT_TESTED`

정차 상태에서만 수행한다. 각 행에 기기·펌웨어·LSPosed·APK SHA-256과 LSPosed 로그(`XposedTeyes/startup:` 줄), 설정의 "로컬 로그"를 함께 기록한다.

| ID | 절차 | 통과 조건 | 상태 |
|---|---|---|---|
| T-DEV-01 | 기본값(완전 부팅만, 5 s)으로 앱 지정 → 재부팅 / ACC OFF→ON 각각 3회 | 재부팅 때만 1회 실행, 로그의 `BOOT ... STARTED` 시각이 부팅 완료 +5 s 이후 | NOT_TESTED |
| T-DEV-01b | 실행 대기 중 SystemUI 강제 재시작(`pkill com.android.systemui`) | 같은 부팅에서 중복 실행 없음 | NOT_TESTED |
| T-DEV-02 | 시작 지연 중·직후 후진 기어 / 수신 통화 | 후방카메라·통화 화면이 가려지지 않음 | NOT_TESTED |
| T-DEV-03 | 단일·분할·접근성 꺼진 분할·삭제된 앱 | 선택과 일치, 분할 미지원 시 첫 앱만 + 안내 | NOT_TESTED |
| T-DEV-04 | BT 폰 연결/해제/통화 중 상태 | 연결 시 1회, 통화 중 재실행 없음, 로그의 무시된 상태값 기록 | NOT_TESTED |
| T-CP-01..05 | `docs/research/carplay.md` | 2B 구현 후 | BLOCKED |
| T-BT-01..05 | `docs/research/bt-mic.md` | 3B 구현 후 | BLOCKED |
| T-REC-01 | 대기 중 "신규 기능 전체 끄기" / "시작 앱 기본값 복원" | 대기 실행 취소, 원래 동작 회복 | NOT_TESTED |
| T-PRIV-01 | 로그·설정 백업·CI 산출물 검토 | 식별번호·전화번호·음성·키·비공개 APK 없음 | 코드 검토 완료, 실기기 산출물은 NOT_TESTED |
