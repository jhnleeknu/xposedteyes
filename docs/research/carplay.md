# CarPlay 해상도·DPI — 기존 Android Auto 설정으로 되는가?

## 결론: 안 된다 (코드로 확인)

*CarLink* 범주의 영상 해상도·너비/높이 여백·DPI 는 모두 다음 한 곳에서만 적용된다 (`XposedTeyes.java` 의 `com.syu.carlink` 블록):

1. 훅 대상: `com.syu.carlink.auto.AutoFragment` 의 메서드 `c` (`auto` 패키지 = Android Auto 화면).
2. 반환값을 `com.google.android.projection.proto.Protos$VideoConfiguration` 빌더로 다시 만들어 `setCodecResolution`, `setWidthMargin/HeightMargin`, `setDensity` 를 바꾼다.
3. `com.google.android.projection.*` 는 Google 의 Android Auto 수신 측 프로토콜(GAL)이다. CarPlay 세션은 Apple 프로토콜이라 이 객체를 만들지도, 읽지도 않는다.

즉 값을 바꿔도 Android Auto 세션이 시작될 때만 쓰이고, CarPlay 연결에는 이 메서드가 불리지 않는다. 그래서 설정 이름을 "Android Auto DPI" 로 바꿔 두었다.

같은 블록의 "SystemUI 유지"(`carlinkKeepSystemUI`)는 CarLink 프로세스 전체의 Activity 에 적용되므로 CarPlay 화면에도 영향을 줄 수 있다. 해상도·DPI 와는 무관하다.

## CarPlay 에 적용하려면

CarLink 의 CarPlay 코드에서 iPhone 에 보내는 화면 정보(픽셀 크기·물리 크기/DPI)를 만드는 곳을 찾아 같은 방식으로 훅해야 한다. 이 저장소에는 CarPlay 관련 클래스가 없어서 위치를 모른다. 찾는 데 필요한 것:

- *CarLink > CarLink 영상 진단 로그* 를 켜고 CarLink 재시작 → CarPlay 1회 연결 → LSPosed 로그의 `XposedTeyes diag:` 줄. 디코더 해상도와, 디코더를 설정한 CarLink 클래스 이름이 나온다. 이것으로 협상 코드의 위치를 좁힐 수 있다.
- 그래도 못 찾으면 CarLink APK (`adb shell pm path com.syu.carlink`) 를 정적 분석해야 한다. 네이티브 라이브러리에서 처리하면 Java 훅만으로는 안 될 수 있다.

## 기기에서 직접 확인하는 법 (1분)

1. *CarLink > 영상 해상도* 를 1920x1080 으로 바꾸고 재부팅(또는 CarLink 강제 종료).
2. CarPlay 연결 → 글씨·아이콘 크기나 선명도가 그대로면 적용되지 않는 것. 같은 상태로 Android Auto 를 연결하면 바뀌어야 한다.
