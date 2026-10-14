# CarPlay 화면 밀도·배율 분석 (CP-02) — 상태: BLOCKED

> 결론: 이 브랜치에서는 CarPlay 화면 제어를 **구현하지 않았다**. 수정 지점을 확인할 CarLink 앱·라이브러리를 분석하지 못했기 때문이다. 아래는 확인한 사실, 확인하지 않은 가설, 그리고 진행에 필요한 최소 자료다.

## 1. 저장소에서 확인한 사실

| 사실 | 근거 |
|---|---|
| 3.0 의 "Custom DPI"·해상도·여백은 `com.syu.carlink.auto.AutoFragment` 의 메서드 `c` 결과(`com.google.android.projection.proto.Protos$VideoConfiguration`)를 다시 만들어 `setDensity`, `setCodecResolution`, `setWidthMargin/HeightMargin` 을 바꾼다 | `src/com/pijulius/xposedteyes/XposedTeyes.java` CarLink 블록 |
| `com.google.android.projection.*` 는 Android Auto(GAL) 수신 측 프로토콜이다. CarPlay 경로와 무관하다 | 동일 |
| 저장소에는 CarPlay 관련 클래스명·훅이 하나도 없다 | 전체 검색 |
| CarLink 패키지명은 `com.syu.carlink` 이며 LSPosed 범위에 "CarLink" 로 포함하도록 README 가 안내한다 | README |

따라서 **Android Auto DPI 를 CarPlay DPI 로 부를 근거가 없다.** 설정 화면에서 기존 항목 이름을 "Android Auto DPI" 로 바꾸고 CarPlay 화면에는 BLOCKED 상태를 표시했다.

## 2. 구분해야 할 세 가지 목표 (CP-01)

| 목표 | 의미 | 일반적으로 정해지는 곳 (가설, CC3 미확인) |
|---|---|---|
| 실제 밀도/레이아웃 | iPhone 이 다른 화면 정보로 UI 를 다시 구성 | 헤드유닛이 세션 시작 시 iPhone 에 알리는 화면 정보(픽셀 크기, 물리 크기 등) |
| 수신 영상 배율 | 이미 받은 영상을 CC3 가 확대·축소 | 디코더 출력 Surface/TextureView 크기·변환 |
| 해상도·여백·위치 | 협상 해상도 또는 렌더링 영역 | 세션 협상 값 또는 뷰 레이아웃 |

세 번째 열은 다른 헤드유닛 구현에서 흔한 구조에 근거한 **가설**이다. 예를 들어 일부 CarPlay 동글 계열 오픈소스 구현은 세션 시작 시 해상도·fps 와 별도의 DPI 값을 동글에 전달한다. CC3 CarLink 가 내장 구현인지, 동글형 모듈인지, 어느 계층(Java/네이티브)에서 값을 만드는지는 확인하지 않았다.

## 3. 이 브랜치가 제공하는 분석 도구

*CarPlay 화면 > CarLink 영상 진단 로그* (기본 OFF, 읽기 전용, `src/com/pijulius/xposedteyes/diag/DiagHooks.java`):

- `MediaCodec.configure` 호출 시 코덱 이름, MIME, 너비·높이, frame-rate, 호출한 앱 클래스·메서드(스택의 첫 비프레임워크 프레임).
- CarLink Activity `onResume` 시 화면 크기·densityDpi.
- 프로세스당 최대 200줄, 인자·반환값을 바꾸지 않음. CarLink 재시작 후 적용.

이 로그로 알 수 있는 것: (a) CarPlay 세션의 실제 디코딩 해상도, (b) 디코더를 설정하는 CarLink 내부 클래스 이름 → 협상 코드를 찾는 출발점. 알 수 없는 것: iPhone 에 보낸 화면 정보, 터치 변환.

## 4. 진행에 필요한 자료 (최소)

| 자료 | 이유 | 없을 때 |
|---|---|---|
| 기기의 CarLink APK 1개 (`adb shell pm path com.syu.carlink` 로 찾은 파일) 와 그 앱이 쓰는 `lib/` 의 `.so` 파일 목록(이름만 먼저) | 화면 정보 생성·협상, 렌더링, 터치 변환 지점을 정적 분석하기 위해 | CP-02 의 클래스·메서드 서명을 확정할 수 없음 → CP-03/04 BLOCKED 유지 |
| CarLink `versionName`/`versionCode` (`adb shell dumpsys package com.syu.carlink \| grep version`) | 버전별 어댑터·구조 검증 | 어댑터를 버전에 묶을 수 없음 |
| 진단 로그를 켠 상태에서 CarPlay 1회 연결한 LSPosed 로그 중 `XposedTeyes diag:` 줄 | 실제 협상 해상도·디코더 설정 위치 | 위 정적 분석만으로 진행 |

기기 식별번호, 전체 기기 정보, 펌웨어 이미지는 필요하지 않다. 제조사 파일은 저장소·PR·CI 산출물에 올리지 않는다(분석 결과만 문서화).

## 5. 자료 확보 후 분석 절차

1. APK SHA-256·버전 기록, jadx 등으로 디컴파일. 문자열 `carplay`, `airplay`, `iap`, `width`, `height`, `dpi`, `ppi`, `physical`, `mm`, `viewArea`, `safeArea`, `MediaCodec`, `Surface`, `MotionEvent`, `touch` 검색.
2. 화면 정보가 Java 에서 만들어지면: 정확한 클래스·메서드 서명·인자 의미·호출 시점 기록 → 버전·구조 검증 어댑터(`carplay/`)에서 값 교체. 네이티브(JNI)로 넘어가면: JNI 진입 메서드 인자 확인, 필요 시 해당 `.so` 분석 범위를 따로 정의.
3. 렌더링: 디코더 출력 뷰의 크기·Matrix 확인. 배율 변경 시 터치 좌표 역변환 지점(뷰 `dispatchTouchEvent` 또는 네이티브 전송 직전) 확인, 프레임워크가 이미 적용한 변환과 중복되지 않게 한다.
4. 구현은 확인된 범위만: 화면 확대만 가능하면 UI 이름도 "영상 확대/축소" 로 한다. 시험 적용(기본 15초, CarLink 가 멈춰도 판단 가능한 위치 — 예: 모듈 앱이나 SystemUI 측 타이머) → 확인 → 확정, 미확정 값은 다음 세션 기본값으로 승격하지 않는다(CP-04).
5. Android Auto·일반 CC3 화면·후방카메라 회귀 확인(T-CP-05).

## 6. 상태 요약

| 항목 | 상태 |
|---|---|
| CarPlay 실제 밀도/레이아웃 | BLOCKED (자료 없음) |
| 영상 배율·여백·위치 | BLOCKED |
| 시험 적용·복원 | BLOCKED (제어 대상 없음) |
| 분석용 진단 로그 | IMPLEMENTED / NOT_TESTED |
