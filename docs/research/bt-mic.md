# Bluetooth 통화 마이크 경로 분석 (BT-02) — 상태: BLOCKED

> 결론: 이 브랜치에서는 마이크 이득·AGC·잡음 억제·에코 제거를 **변경하는 기능을 구현하지 않았다.** 일반 BT(HFP) 통화의 송신 음성이 어느 계층에서 처리되는지 확인하지 못했기 때문이다. 읽기 전용 진단 도구만 추가했다.

## 1. 문제 정의 (BT-01)

같은 차량에서 CarPlay 통화는 선명하지만 CC3 의 일반 BT 통화에서는 내장 마이크 음성이 멀게 들린다는 사용자 관찰. 이 관찰만으로 CarPlay 의 입력원·처리 위치를 알 수 없고, CarPlay 처리를 BT 경로에 복사할 수 있다고 가정하지 않는다. 외장 마이크·배선·저항 회로는 범위 밖이다.

## 2. 후보 경로 — 확정 아님

```text
물리 마이크 → 입력/코덱 → [ Android 오디오(HAL) | 제조사 DSP | 별도 BT 모듈 ] → HFP SCO 송신 → 휴대폰 → 상대방
CarLink/CarPlay 캡처·통화 경로는 별도로 추적
```

애프터마켓 헤드유닛에서는 BT 칩/모듈이 SCO 오디오를 코덱(PCM/I2S 또는 아날로그)과 직접 주고받아 Android `AudioRecord` 를 거치지 않는 구성이 흔하다(가설, CC3 미확인). 이 경우 Java 훅으로는 PCM 에 접근할 수 없고, 제어 지점은 제조사 서비스·HAL·모듈 명령일 수 있다.

## 3. 확인한 사실

| 사실 | 근거 |
|---|---|
| 저장소의 `com.syu.bt` 관련 코드는 리소스 교체(아이콘·앱 이름)뿐이다 | `XposedTeyes.handleInitPackageResources` |
| SystemUI 의 `com.syu.view.BluetoothControl.updata(int)` 로 연결 상태를 알 수 있다 | 3.0 BT 실행 기능 |
| 위 두 가지는 PCM 오디오 접근이나 마이크 제어 가능성을 뜻하지 않는다 | — |

후보 속성 `persist.btmic.gain`, `persist.btspk.gain`, `persist.audio.mic.senstivity` 는 **검증 대상**일 뿐이다. 존재·읽기·쓰기 성공은 효과의 근거가 아니며, 이 브랜치는 이 값을 쓰지 않는다.

## 4. 이 브랜치가 제공하는 분석 도구

1. *BT 통화 마이크 > 경로 진단 (읽기 전용)* — `BtMicDiagnostics.java`
   - 후보 속성 값(읽을 수 있을 때), `AudioManager` 모드·SCO 상태·마이크 음소거, 입력 장치 종류.
   - 녹음·입력 점유·쓰기 없음. 대기 중과 BT 통화 중 각각 실행해 비교.
2. *오디오 경로 진단 로그* (기본 OFF) — `DiagHooks.java`, 대상 `com.syu.bt`, `com.syu.carlink`, `com.syu.ms`, `com.syu.us`, SystemUI
   - `AudioRecord` 생성(입력원·샘플레이트·채널·세션, 호출 클래스), `AudioManager.setParameters`/`AudioSystem.setParameters` 문자열, `setMode`, `AcousticEchoCanceler/NoiseSuppressor/AutomaticGainControl.create`, 이름에 mic/hfp/sco/btmic/btspk/aec/agc 가 들어간 `SystemProperties.set`.
   - 인자·반환값을 바꾸지 않음. 프로세스당 최대 200줄. 음성 데이터·전화번호는 기록하지 않음.

이 로그로 답할 질문: BT 통화 시 Java 캡처 세션이 생기는가? 통화 시작/종료에 어떤 오디오 파라미터 문자열이 쓰이는가(HFP 경로 전환·이득 명령 후보)? 위 속성은 누가 언제 쓰는가?

## 5. 진행에 필요한 자료 (최소)

| 자료 | 이유 | 없을 때 |
|---|---|---|
| 진단 로그를 켠 상태에서 BT 통화 1회(발신·종료)를 한 LSPosed 로그의 `XposedTeyes diag:` 줄 | 실제 캡처 세션 유무, 파라미터·속성 쓰기 지점 | 경로 계층을 확정할 수 없음 |
| `adb shell getprop` 결과 중 `mic`, `bt`, `hfp`, `sco` 를 포함한 줄만 | 후보 속성의 존재·현재값 | 진단 화면 값으로 대체(일부만) |
| 위 속성 이름을 포함하는 기기 파일 목록: `grep -l` 결과(이름만) — `/system/bin`, `/vendor/bin`, `/system/lib*`, `/vendor/lib*`, `/system/etc/init`, `/vendor/etc/init` | 값을 실제로 읽는 프로그램·시점 식별(BT-02 의 "이득값 소비 지점") | 속성 효과를 추정할 근거 없음 |
| `com.syu.bt`, `com.syu.ms` APK (필요한 경우에만, 위 로그로 Java 경로가 확인된 뒤) | 제조사 제어 API 분석 | — |

전화번호, 통화 기록, 연락처, 녹음 파일, 기기 식별번호는 필요하지 않다.

## 6. 자료 확보 후 절차 (BT-04/05)

1. 이득값 소비 지점과 허용 범위·단위(단계인지 dB 인지)·원래 값·적용 시점을 문서화. dB 변환이 확인되지 않으면 UI 는 "단계"로 표시.
2. 실제 HFP 송신에 영향을 주는 제어만 백엔드로 구현. 원래 값은 최초 적용 직전에 기록, 복원 경로 포함. 정차 상태 명시적 시험 기능부터.
3. AEC/NS/AGC: 실제 대상 세션과 효과 지원 여부 확인. 독립 세션에 효과를 만드는 것으로 HFP 경로를 처리했다고 보지 않는다.
4. 비교 시험: `BT 순정` / `BT 변경 후` / `CarPlay 참고`, 같은 위치·거리·음량·공조, 상대측 수신 기록 우선. 레벨·무음 잡음·클리핑·말끝 손실·에코·명료도 기록. 샘플레이트·정규화가 다른 파일의 RMS 만으로 판단하지 않는다.
5. 금지: 이득 무차별 대입, 미확인 제조사 명령 전송, 통화 중 반복 실험, 전역 오디오 설정 덮어쓰기.

## 7. 상태 요약

| 항목 | 상태 |
|---|---|
| 입력 이득 / AGC / NS / AEC / 음성 프로파일 제어 | BLOCKED (제어 경로 미확인) |
| 경로 진단 화면 (읽기 전용) | IMPLEMENTED / NOT_TESTED |
| 오디오 경로 진단 로그 | IMPLEMENTED / NOT_TESTED |
| 음질 개선 입증 | 해당 없음 (제어 미구현) |
