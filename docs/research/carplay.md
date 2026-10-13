# CarPlay DPI·화면 배율 분석 (CP-01, CP-02) — 상태: BLOCKED

## 1. 결론 요약

- 저장소에 있는 화면 관련 훅은 **Android Auto 경로뿐**이다. CarPlay 경로의 클래스·메서드·네이티브 라이브러리는 이 저장소와
  이 작업 환경에서 확인할 수 없었다(CarLink APK가 없음, 기기 없음).
- 따라서 CP-03(제어)·CP-04(시험 적용·복원)는 시작하지 않았고, 설정 화면의 CarPlay 조절 항목은 비활성 + "미확인"으로만 표시한다.
- 기존 `carlinkDPI` 설정은 이름과 설명을 "Android Auto DPI"로 바꿔 CarPlay용으로 오해되지 않게 했다. 동작은 바꾸지 않았다.

## 2. 저장소에서 확인한 사실 (기준 커밋 `4c376ab`)

| 위치 | 내용 | 의미 |
|---|---|---|
| `XposedTeyes.java` `com.syu.carlink` 분기 | `com.syu.carlink.auto.AutoFragment.c(...)`의 반환값(`Protos$VideoConfiguration`)을 빌더로 다시 만들어 `setCodecResolution`, `setWidthMargin/HeightMargin`, `setDensity` 적용 | Google Android Auto 프로젝션 프로토콜(GAL)의 영상 설정. CarPlay와 무관 |
| 같은 분기 주석 | `c(width, height, dpi, realDpi, fps, depth)` 형태 인자 추정 주석 | AA 수신기 설정 |
| `GalReceiver$CarInfo`, `SystemUiHider`, `LocationSensors` 훅 | AA 개발자 모드·UI 표시·주행 상태 | AA 전용 |
| 리소스 훅 `logo_fragment_tzy2` 등 | CarLink 첫 화면 꾸미기 | 연결 프로토콜과 무관 |

CarPlay 세션은 일반적으로 iAP2 인증·제어 채널 위에서 AirPlay 기반 화면 스트림(H.264)을 받는 구조이고, 헤드유닛 앱이 디스플레이 크기·물리 치수·
선호 해상도 등을 세션 설정으로 휴대폰에 알린다. 이 일반 구조는 **CarLink 내부 구현을 확인한 것이 아니며**, 실제 수정 지점은 APK를 봐야 정할 수 있다.

## 3. 구현 가능성 후보 (검증 전 가설)

| 목표 | 후보 지점 | 성공 판정 | 위험 |
|---|---|---|---|
| 실제 표시 밀도/레이아웃 | 세션 시작 시 휴대폰에 보내는 화면 정보(해상도·물리 크기·DPI 유사 값)를 만드는 Java 메서드 또는 JNI 인자 | CarPlay 아이콘 수·정보량이 달라짐 | 값이 네이티브 안에서 만들어지면 Java 훅으로 불가 |
| 수신 영상 배율·위치 | 디코더 출력 Surface/TextureView의 크기·변환 행렬 | 영상 확대·이동과 터치가 일치 | 터치 역변환 누락 시 오작동 |
| 해상도·여백 | 세션 협상 값 또는 렌더링 영역 | 디코더 출력 해상도 변화 | 휴대폰이 거절하면 연결 실패 → 시험 적용 필수 |
| 터치 | `MotionEvent` → 좌표 변환 → 송신(Java 또는 JNI) | 모서리·드래그·멀티터치 일치 | 프레임워크·앱 이중 변환 |

## 4. 필요한 자료 (사용자가 제공해야 진행 가능)

| 자료 | 이유 | 없을 때 막히는 항목 |
|---|---|---|
| `com.syu.carlink` **APK 파일** (base.apk) | CarPlay 세션 설정·디코더·터치 경로를 정적 분석 | CP-02 전체, CP-03 전체 |
| 같은 앱의 네이티브 라이브러리 목록과 해당 `.so` 파일 | 화면 정보·터치가 JNI 안에서 처리되는지 판단 | 실제 DPI 변경 가능성 판정 |
| 설정 앱 "CarPlay 화면 → CarLink 정보" 내보내기 결과 | 버전·SHA-256·CarPlay 관련 클래스 이름 목록(이름만) | 버전별 어댑터 범위 |

수집 방법(읽기 전용, 루트 불필요한 경우가 많음):

```sh
adb shell pm path com.syu.carlink            # APK 경로 확인
adb pull <위 경로> carlink.apk                # APK 복사
adb shell dumpsys package com.syu.carlink | grep -E "versionName|versionCode|legacyNativeLibraryDir"
```

- 기기 식별번호·계정·통화 기록은 필요하지 않다.
- 제조사 APK는 **공개 저장소·PR·CI 산출물에 올리지 않는다.** 분석 결과(클래스·메서드 서명, 버전, 해시)만 이 문서에 기록한다.

## 5. 2B 착수 조건과 설계 원칙 (명세 7.3–7.4)

- 확인된 백엔드만 CarLink 버전(versionCode + 메서드 구조·반환형 확인)별 어댑터로 활성화. 구조 불일치 시 원래 동작 유지.
- 배율은 100%에서 시작, 검증한 범위만 노출. DPI·해상도 범위는 백엔드 확인 후 정의.
- 시험 적용 15 s 확인 타이머는 CarLink 프로세스 밖(설정 앱)에서 관리하고, 확인되지 않은 값은 영구 값으로 승격하지 않는다.
- 영상 확대만 가능하면 "영상 확대/축소"로만 표시한다. 실제 CarPlay DPI가 바뀌지 않는데 DPI 변경이라 보고하지 않는다.
- Android Auto·CC3 일반 화면·후방카메라에는 적용하지 않는다.

## 6. 시험 (CP 실기기 항목)

T-CP-01..05는 2B 구현 후 수행한다. 현재 상태: BLOCKED.
