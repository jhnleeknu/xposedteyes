# 복구 절차

## 1. 앱 안에서

| 상황 | 방법 | 효과 |
|---|---|---|
| 시작 앱이 원치 않게 뜸 | 시작 앱 관리 → 자동 실행 끄기 | 대기·재시도 즉시 취소(SystemUI에 알림), 실행 시점에도 재확인 |
| 신규 기능 전체 중지 | 고급/진단 → 신규 기능 전체 끄기 | 시작 앱·BT 실행 중지. 기존 분할화면·카메라·Android Auto 기능은 그대로 |
| 시작 앱 설정 초기화 | 시작 앱 관리 → 시작 앱 기본값 복원 | 시작·BT 실행 키 삭제(기본값 OFF) |
| 모든 설정 초기화 | 고급/진단 → 모든 설정 기본값 복원 | 전체 기본값. 후킹 기능은 재부팅 후 반영될 수 있음 |
| 설정 옮기기 | 설정 내보내기 / 가져오기 | JSON 파일. 가져오기는 화이트리스트·형식·범위 검증 후 확인 대화상자 |

마이그레이션·가져오기·초기화 직전 값은 앱 내부 `settings_backup` 파일에 자동 보관된다(외부 저장소 아님).

## 2. 앱을 열 수 없을 때

1. LSPosed 관리자에서 XposedTeyes 모듈을 끄고 재부팅한다. 모든 훅이 사라진다.
2. LSPosed 관리자에 접근할 수 없으면 Magisk 안전 모드(기기별 방법) 또는 `adb shell`에서 모듈을 비활성화한다.
   이 저장소는 부팅 장애 자체의 자동 복구를 보장하지 않는다.
3. SystemUI 시작 앱 엔진은 오류 5회 후 스스로 멈춘다. 다른 훅은 기존과 같다.

## 3. 백업 파일 형식

```json
{
  "format": "xposedteyes-settings",
  "schemaVersion": 1,
  "appVersion": "3.1",
  "values": { "startupEnabled": true, "startupDelayMs": 5000, "startupPrimary": "com.waze/com.waze.FreeMapAppActivity" }
}
```
기기 식별정보·시각·계정 정보는 들어가지 않는다. 가져오기는 알려진 키만 받으며 shell 명령·클래스 이름을 실행하지 않는다.

## 4. 설치와 서명

- CI 산출물은 러너마다 새로 생성되는 debug 키로 서명된다. **이미 설치된 XposedTeyes(원 저작자 릴리스 또는 다른 CI 빌드)를 덮어쓰기 업데이트할 수 없다.**
  설치하려면 기존 앱을 제거해야 하며 이때 설정이 지워진다. 먼저 "설정 내보내기"(신규 버전) 또는 기존 값 기록을 해 둔다.
- 반복 업데이트가 필요하면 저장소 관리자가 테스트 키를 만들어 GitHub Secrets에 두고, 빌드 시 `XT_KEYSTORE_PATH`,
  `XT_KEYSTORE_PASSWORD`, `XT_KEY_ALIAS`, `XT_KEY_PASSWORD` 환경 변수로 전달한다(`app/build.gradle`의 `testKey`).
  워크플로에는 아직 이 단계를 넣지 않았다. 키 파일은 커밋하지 않는다(`.gitignore`).
- `bin/com.pijulius.xposedteyes.apk`는 원 저작자의 이전 바이너리이며 이번 소스로 만든 빌드가 아니다.

## 5. 되돌리기

이 변경 전체를 되돌리려면 브랜치 커밋을 revert한다. 저장된 새 키(`startup*`, `btLaunch*` 등)는 이전 버전에서 무시되며,
레거시 키(`launchOnStartup` 등)는 마이그레이션 때 삭제하지 않았으므로 이전 버전이 그대로 읽는다.
