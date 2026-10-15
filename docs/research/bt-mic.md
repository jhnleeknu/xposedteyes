# Bluetooth 통화 마이크 이득 — APK 가 꼭 필요한가?

## 결론: 처음에는 필요 없다

먼저 **기기에서 루트 셸로** 후보 속성이 실제로 쓰이는지 확인한다. 속성으로 조절되면 APK 분석 없이 "속성 값 쓰기" 설정 하나로 구현할 수 있다. APK 는 아래 방법이 모두 막혔을 때만 필요하다.

후보 (사용자 보고 기반, 효과 미확인): `persist.btmic.gain`, `persist.btspk.gain`, `persist.audio.mic.senstivity`

## CarLink 2.25 분석 결과 (네이티브 라이브러리 역어셈블)

`persist.btmic.gain` 은 이름과 달리 **CarPlay 통화** 마이크 설정이다. CarLink 의 `LsecGetConfigs` (`libAirPlay.so`, `libcarplayplugin_*.so`) 가 CarPlay 세션에서 아래 값을 읽는다.

| 속성 | 기본값 | 로그 표시 / 용도 |
|---|---|---|
| `persist.btmic.gain` | 5 | "cpblink volume" — blink 에코 제거기(libblinkAEC) 쪽 마이크 음량 |
| `persist.lsec.cp.micvol` | 96 | "add mic volume" — 추가 마이크 음량 |
| `persist.lsec.cp.useblink` | 1 | blink AEC 사용 여부 |
| `persist.lsec.cp.noise` | `1,0,0` | 잡음 제거(denoise) 설정 |
| `persist.lsec.cp.paramas` | `10,0,0` | 에코 제거 지연·모드·NS 모드 |
| `persist.lsec.cp.micLR`, `sys.lsec.cp.telvol` | 2, 120 | 마이크 좌/우 채널, 통화 음량 |
| `persist.lsec.cp.agc`, `persist.lsec.cp.aec` (liblsaudioprocess) | — | AGC `enable,metrics,dbfs_level,gain_db`, AEC `delay,aec_level,ns_level` |

CarPlay 통화가 선명한 이유는 CarLink 가 자체적으로 WebRTC·blink 기반 잡음 억제·에코 제거·AGC 를 하기 때문일 가능성이 높다.

**일반 BT 통화(com.syu.bt, BT 모듈)는 이 APK 들에 없다.** 위 값들이 일반 BT 통화에 영향을 준다는 근거는 없다. 일반 BT 통화 마이크를 다루려면 `com.syu.bt` APK(기기에서 `adb shell pm path com.syu.bt`)나 BT 모듈 쪽 분석이 필요하고, 그 전에 아래 2번(`grep`)으로 `btmic` 를 읽는 다른 프로그램이 있는지 먼저 확인한다.

## 앱의 CarPlay 설정 (CarPlay 통화 전용)

*CarPlay > 통화 마이크* 에서 루트로 아래 속성을 바꾼다. 적용 후 루트 `getprop` 으로 다시 읽어 확인하고, 다음 CarPlay 통화부터 반영된다.

- 마이크 음량: `persist.btmic.gain` 3~8 (CarLink 기본 5, 2단계 작게 ~ 3단계 크게).
- 잡음 제거: `persist.lsec.cp.noise` 첫 칸만 1/0 으로 바꾸고 나머지 두 칸은 유지.
- *CarLink 기본값으로 되돌리기*: 5 와 잡음 제거 켜짐.

단계 하나가 몇 dB 인지는 확인하지 못했다. 소리가 깨지면 한 단계 내린다.

## 1. 값이 있는지 (adb, 루트 불필요)

```sh
adb shell getprop | grep -iE "btmic|btspk|mic"
```

앱의 *BT 통화 마이크 > 경로 진단* 화면도 같은 값을 보여 준다(대기 중 / 통화 중 각각).

## 2. 누가 그 값을 읽는지 (루트)

```sh
adb shell su -c 'grep -rl "persist.btmic" /system /vendor 2>/dev/null'
adb shell su -c 'grep -rn "btmic" /system/etc/init /vendor/etc/init 2>/dev/null'
```

- 실행 파일·라이브러리(예: 블루투스 모듈 데몬, 오디오 HAL)가 나오면 그 프로그램이 값을 읽는다는 뜻이다. init `.rc` 의 `on property:persist.btmic.gain=*` 같은 줄이 있으면 값이 바뀔 때 무엇을 하는지도 보인다.
- 아무것도 안 나오면 속성은 쓰이지 않는 것이고, 이때만 APK(`com.syu.bt`, `com.syu.ms`) 분석으로 넘어간다. *오디오 경로 진단 로그* 를 켜고 BT 통화 1회를 한 LSPosed 로그가 있으면 어느 앱이 어떤 오디오 파라미터를 쓰는지 먼저 알 수 있다.

## 3. 효과 확인 (정차 상태, 루트)

```sh
adb shell getprop persist.btmic.gain          # 원래 값 기록
adb shell su -c 'setprop persist.btmic.gain <원래값+1>'
```

- 값이 언제 반영되는지 모르므로 BT 재연결(필요하면 재부팅) 후 통화한다.
- 상대방에게 같은 문장·같은 거리로 말하고, 원래 값과 번갈아 비교한다. 한 번에 한 단계씩만 바꾸고, 소리가 깨지면(클리핑) 그 전 값이 상한이다.
- 끝나면 원래 값으로 되돌린다: `setprop persist.btmic.gain <원래값>`.

## 4. 효과가 있으면

설정 화면에 "BT 마이크 이득 (단계)" 을 추가한다: 확인된 범위만 선택 가능, 원래 값 표시·복원 버튼, 루트로 `setprop`. 단위가 dB 인지 확인되지 않으면 "단계" 로 표시한다.

## 지금 앱에 있는 것

- *BT 통화 마이크 > 경로 진단* : 후보 속성 값, 오디오 모드·SCO 상태, 입력 장치 (읽기 전용).
- *오디오 경로 진단 로그* (기본 OFF): Teyes BT/CarLink/시스템 앱의 녹음 세션, 오디오 파라미터, mic 관련 속성 쓰기를 LSPosed 로그에 남긴다. 값을 바꾸지 않고 녹음하지 않는다.
