# 빌드 · 서명 · 설치

## 고정한 도구 버전 (BUILD-01)

| 항목 | 버전 | 근거 |
|---|---|---|
| Gradle Wrapper | 8.14.3 (배포본 SHA-256 고정) | AGP 8.11 최소 요구 Gradle 8.13 이상 |
| Android Gradle Plugin | 8.11.1 | JDK 17 이상, compileSdk 34 지원 |
| JDK | 17 이상 (CI: Temurin 17, 로컬 검증: OpenJDK 21) | AGP 8.x 요구사항 |
| compileSdk / build-tools | 34 / 34.0.0 | 빌드용. 런타임 동작과 무관 |
| minSdk / targetSdk | **28 / 28 (원본 그대로)** | 아래 참고 |
| Xposed API | `lib/api-82.jar`, `compileOnly` | 런타임 프레임워크 클래스를 APK에 넣지 않음 (dexdump로 `de/robv` 클래스 0개 확인) |
| JUnit / org.json(테스트 전용) | 4.13.2 / 20231013 | JVM 단위 테스트 |

`targetSdk`를 올리지 않은 이유: `MODE_WORLD_READABLE` 설정 공유(LSPosed `xposedsharedprefs`)와 백그라운드 Activity 실행 규칙이 targetSdk에 따라 달라진다. 올려야 한다면 별도 PR에서 CC3(Android 10) 실기기로 설정 공유와 자동 실행 회귀를 확인해야 한다.

기존 Eclipse/ADT 구조(`AndroidManifest.xml`, `src/`, `res/`, `assets/`)는 파일 이동 없이 `build.gradle`의 source set으로 연결했다. 단위 테스트는 `test/`에 있다.

## 명령

```sh
# local.properties 에 sdk.dir=<Android SDK 경로> 지정 (커밋하지 않음)
./gradlew testDebugUnitTest   # JVM 단위 테스트
./gradlew lintDebug           # lint (lint-baseline.xml 은 3.0 기존 코드·리소스의 경고만 포함)
./gradlew assembleDebug       # build/outputs/apk/debug/xposedteyes-debug.apk
```

CI(`.github/workflows/build.yml`)는 위 세 작업을 실행하고 APK, 소스 SHA, 버전, APK SHA-256(`BUILD-INFO.txt`), 테스트·lint 보고서를 산출물로 올린다. 액션은 커밋 SHA로 고정했다.

## 서명과 설치 (중요)

- `bin/com.pijulius.xposedteyes.apk` 는 원 저작자가 서명한 3.0 바이너리이며 새 빌드 결과가 아니다. 재사용하지 않는다.
- 새 debug APK 는 빌드한 컴퓨터의 debug 키로 서명된다. CI 산출물도 실행마다 다른 임시 키로 서명될 수 있다. **원본 APK 위에 덮어쓰기 설치(업데이트)는 서명이 달라 실패한다.** CI 산출물은 시험용이지 업데이트 경로가 아니다.
- 원본에서 옮겨 올 때:
  1. 기존 앱의 *고급/진단 > 설정 내보내기* 는 3.0 에 없으므로, 필요한 값(분할화면 비율, 카메라 위치 등)을 기록해 둔다.
  2. 기존 앱 삭제 → 새 APK 설치 → LSPosed 에서 모듈과 범위(Android Framework, SystemUI, CarLink, UI Server 등)를 다시 지정 → 재부팅.
  3. 설정을 다시 입력한다. 이후 업데이트는 같은 키로 서명한 APK 로만 덮어쓰기 설치한다.
- 반복 시험용 고정 키가 필요하면 저장소 밖(예: CI 암호화 secret)에 보관하고 `signingConfigs` 를 로컬/CI 에서만 주입한다. 개인 키·keystore 는 저장소와 PR 에 넣지 않는다(`.gitignore` 에 `*.jks`, `*.keystore`).

## 라이선스

저장소에 라이선스 파일이 없다. 원 저작자(pijulius / Istvan Petres) 표기를 유지하고, 확인되지 않은 라이선스를 새로 부여하지 않았다. 제조사 APK·라이브러리·펌웨어는 저장소와 CI 산출물에 포함하지 않는다.
