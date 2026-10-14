# CarPlay 해상도·DPI — 기존 Android Auto 설정으로 되는가?

## 결론: 안 된다 (코드로 확인)

*CarLink* 범주의 영상 해상도·너비/높이 여백·DPI 는 모두 다음 한 곳에서만 적용된다 (`XposedTeyes.java` 의 `com.syu.carlink` 블록):

1. 훅 대상: `com.syu.carlink.auto.AutoFragment` 의 메서드 `c` (`auto` 패키지 = Android Auto 화면).
2. 반환값을 `com.google.android.projection.proto.Protos$VideoConfiguration` 빌더로 다시 만들어 `setCodecResolution`, `setWidthMargin/HeightMargin`, `setDensity` 를 바꾼다.
3. `com.google.android.projection.*` 는 Google 의 Android Auto 수신 측 프로토콜(GAL)이다. CarPlay 세션은 Apple 프로토콜이라 이 객체를 만들지도, 읽지도 않는다.

즉 값을 바꿔도 Android Auto 세션이 시작될 때만 쓰이고, CarPlay 연결에는 이 메서드가 불리지 않는다. 그래서 설정 이름을 "Android Auto DPI" 로 바꿔 두었다.

같은 블록의 "SystemUI 유지"(`carlinkKeepSystemUI`)는 CarLink 프로세스 전체의 Activity 에 적용되므로 CarPlay 화면에도 영향을 줄 수 있다. 해상도·DPI 와는 무관하다.

## CarPlay 화면 크기는 어디서 정해지나 (CarLink 2.25.0325.1438 정적 분석)

공개 배포 APK(docs/research/teyes-apps.md)를 디컴파일 없이 `dexdump` 바이트코드와 라이브러리 문자열로 확인했다. 클래스 이름은 난독화돼 있어 버전마다 다를 수 있다.

- CarPlay 자체는 네이티브(`libAirPlay.so`, `libcarplayplugin_*.so`, `libcps_*.so`)에서 돌고, Java 쪽(`com.syu.carlink.carplay.*`)은 디코딩된 영상만 받아 그린다.
- CarPlay 서버 연결 시 `c.n.run()` 이 `f.a` 의 값으로 정수 배열 11칸을 만들어 `f.a.k(218, int[], String[])` 로 네이티브 `CarplayServer.ICarplayService` 에 보낸다.
  - `[2],[3]` 표시 너비·높이(px), `[4],[5]` 동일 값, **`[6]` 물리 너비(mm)**, **`[7]` 물리 높이(mm) = 높이/너비 × 물리 너비**, `[10]` FPS.
- 물리 너비는 `f.a` 생성자에 **221 mm 로 고정**돼 있다. 2022·2023 버전도 같다.
- iPhone 은 픽셀 수와 물리 크기(mm)로 CarPlay UI 크기를 정한다. 따라서 물리 너비 값을 바꾸면 CarPlay 글씨·아이콘 크기(레이아웃)가 바뀔 가능성이 높다 — **실기기 미확인**.

## 구현: 설정 > CarLink > CarPlay UI 크기

- 선택지 70 / 80 / 90 / **100(기본, 변경 없음)** / 110 / 125 / 150 %.
- CarLink 프로세스에서 `f.a.k(int, int[], String[])` 를 훅해 명령이 218 이고 배열이 11칸일 때만 `[6]`·`[7]`(물리 너비·높이 mm)을 `원래값 × 100 / 퍼센트` 로 바꾼다. 픽셀 값은 건드리지 않으므로 영상 크기·터치 좌표는 그대로다 (`carplay/CarplayScale.java`).
- 원래값을 기준으로 계산하므로 CarLink 가 221 mm 대신 다른 값을 쓰는 버전이어도 비율은 유지된다.
- `f.a.k` 이름은 2.22·2.23·2.25 에서 같음을 확인. 없는 버전에서는 훅을 건너뛰고 LSPosed 로그에 "not supported" 를 남긴다.
- 다음 CarPlay 연결부터 적용. 적용되면 LSPosed 로그에 `XposedTeyes carplay: UI 125%, physical mm 221x124 -> 177x99` 같은 줄이 남는다.
- **실기기 미확인**: iPhone 이 물리 크기를 몇 단계로만 반영할 수 있어, 작은 변화(90·110%)는 차이가 없을 수도 있다.

## Android Auto 훅은 2.25 에서도 유효

`com.syu.carlink.auto.AutoFragment.c(IIIIII, Integer, Float)` 가 `Protos$VideoConfiguration` 을 반환 — 3.0 의 훅 대상과 일치한다.

## 기기에서 직접 확인하는 법 (1분)

1. *CarLink > 영상 해상도* 를 1920x1080 으로 바꾸고 재부팅(또는 CarLink 강제 종료).
2. CarPlay 연결 → 글씨·아이콘 크기나 선명도가 그대로면 적용되지 않는 것. 같은 상태로 Android Auto 를 연결하면 바뀌어야 한다.
