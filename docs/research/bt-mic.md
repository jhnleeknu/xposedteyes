# Bluetooth 통화 마이크 경로 분석 (BT-01, BT-02, BT-03) — 상태: 진단만 IMPLEMENTED, 제어 BLOCKED

## 1. 결론 요약

- 일반 BT 통화(HFP) 송신 경로에서 실제로 소비되는 이득·처리 제어를 **확인하지 못했다.** 기기·제조사 APK·HAL 구성 파일이 없기 때문이다.
- 그래서 어떤 시스템 속성도 쓰지 않고, 이득·AGC·NS·AEC 조절 항목은 비활성 "미확인"으로 표시한다.
- 구현한 것은 **읽기 전용 진단 화면**(BT-03)이다: 후보 속성 값, AudioManager 상태(모드, SCO, 마이크 음소거, 입력 장치 유형),
  앱 캡처 세션용 AEC/NS/AGC 지원 여부, `com.syu.bt` 버전. 녹음·값 쓰기·업로드를 하지 않는다.

## 2. 분석 대상 경로 (확정된 배선이 아님)

```text
물리 마이크 → 입력 코덱 → [Android AudioFlinger/HAL | 제조사 DSP | 별도 BT 모듈 내부 처리]
           → HFP SCO 송신 → 휴대폰 → 통화 상대
CarLink/CarPlay 통화는 별도 경로로 추적 (CarPlay는 휴대폰 쪽 처리·다른 캡처 경로일 수 있음)
```

FYT 계열 헤드유닛은 BT 칩 모듈이 HFP를 직접 처리하고 Android에는 제어 명령만 오가는 구성일 가능성이 있다. 이 경우 Android `AudioRecord`나
`AudioEffect`로는 송신 PCM에 접근할 수 없다. **이것은 확인된 사실이 아니라 우선 검증할 가설이다.**

## 3. 저장소에서 확인한 사실

| 위치 | 내용 | 의미 |
|---|---|---|
| `XposedTeyes.java` `com.syu.bt` 리소스 훅 | 다이얼 패드 이미지·앱 이름 교체 | UI만. 오디오 경로와 무관 |
| SystemUI `com.syu.view.BluetoothControl.updata(int)` | BT 상태 아이콘 갱신 | 연결 상태만 알 수 있음. PCM 접근 근거 아님 |

`com.syu.bt` 패키지가 있다는 사실만으로 PCM에 접근할 수 있다고 보지 않는다.

## 4. 후보 속성 (적용 지시 아님)

`persist.btmic.gain`, `persist.btspk.gain`, `persist.audio.mic.senstivity`

진단 화면은 값을 **읽기만** 한다. 다음이 확인되기 전에는 쓰지 않는다: 이 값을 읽는 코드(제조사 서비스·HAL·BT 모듈 명령), 읽는 시점(부팅·통화 시작),
단위·방향·범위, 원래 값, 변경 전후 실제 통화 상대측 결과. 근거 없는 권장값(예: 7→8→9)을 쓰지 않는다.

## 5. 필요한 자료

| 자료 | 이유 | 없을 때 막히는 항목 |
|---|---|---|
| `com.syu.bt` APK, MainServer(`com.syu.ms` 등 BT 명령을 보내는 서비스) APK | HFP 제어 명령·이득 설정 API 탐색 | BT-02, BT-04 |
| `/vendor/etc/audio_policy_configuration.xml`, `mixer_paths*.xml`(있으면) | 통화 입력 경로·SCO 장치·믹서 이득 컨트롤 확인 | 입력 경로 판정 |
| `getprop`에서 `bt`, `mic`, `audio` 관련 줄 | 후보 속성 실제 값과 다른 후보 | 속성 의미 추적 |
| 진단 화면 내보내기 결과(통화 중·통화 아님 각 1회) | 통화 중 오디오 모드·SCO 상태 변화 | Android 경로 사용 여부 판정 |

```sh
adb shell pm path com.syu.bt
adb shell getprop | grep -iE "btmic|btspk|mic|audio" > props.txt
adb shell cat /vendor/etc/audio_policy_configuration.xml > audio_policy.xml
```
통화 녹음·통화 상대 음성은 요청하지 않는다. 기기 식별번호도 필요 없다.

## 6. 3B 착수 조건과 원칙 (명세 8.4–8.5)

- 실제 HFP 송신에 영향을 주는 제어 경로를 확인한 항목만 활성화한다. 제조사 값이 단계 인덱스이면 UI도 "단계"로 표시하고 dB로 표시하지 않는다.
- AEC는 특정 캡처 세션에 붙는 효과다. 앱 자체 테스트 세션에서 효과가 생성된다고 HFP 경로를 처리한다고 보지 않는다.
- 이득 무차별 대입·미확인 제조사 명령·통화 중 반복 실험·전역 오디오 설정 덮어쓰기 금지. 정차 상태의 명시적 시험 기능으로 시작한다.
- 비교: `BT 순정`, `BT 변경 후`, `CarPlay 참고`. 같은 조건, 상대측 수신 기록과 로컬 캡처 구분. RMS만으로 개선 판정 금지.
- 실기기가 없으면 "실기기 미검증"으로 보고하고 개선 수치·파형을 만들어 넣지 않는다.

## 7. 시험

T-BT-01..05: BLOCKED (3B 미착수). 진단 화면 자체의 실기기 동작도 NOT_TESTED.
