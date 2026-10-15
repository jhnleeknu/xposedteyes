# teyes.com.au "Apps" 페이지 APK 정리

출처: https://www.teyes.com.au/pages/apps (2026-10 확인). 매니페스트(`aapt dump badging`)와 문자열만 읽었다. 설치·실행하지 않았고 APK 는 저장소에 넣지 않는다.

| 페이지 이름 | 파일 | 패키지 / 버전 | 하는 일 |
|---|---|---|---|
| TEYES | Teyes__v8.3.apk | `com.yf.teyesview` 8.3 | `cc3.teyes.ru` / `api.teyes.ru` 를 띄우는 웹뷰 앱 + 자체 업데이트 다운로더. TEYES 온라인 서비스 창구 |
| OBDII 2.0.20 ~ 2.0.36 | Teyes OBD2-v2.0.xx.apk | `com.jacky.teyesx` | TEYES OBD2 동글로 엔진 정보(속도·RPM·온도 등) 표시. 페이지 안내: CC3 2K 는 2.0.29 |
| DVR | DVR TZY_release-1.8.2 | `com.teyes.carkit` 1.8.2 (system uid) | TEYES USB 블랙박스 녹화·재생 |
| TPMS | TEYES TPMS.apk | `com.tpms3` 1.13 | 타이어 공기압 센서(TPMS) 표시 |
| 360 App | 360 Panorama-app.zip | `com.syu.fourcamera2` (system uid) | 360° 4채널 카메라 (360 모델 전용) |
| CARLINK (2021) | Carlink 21.0322.1027.apk | `com.syu.carlink` 21.0322 "Car Link" | CarPlay / Android Auto (구버전) |
| CARLINK - 22/09/2022 | Trial Fix ...carlink(1).apk | `com.syu.carlink` 2.22.0909 "Car Link 2.0" | 〃 |
| CARLINK - July 2023 | 190000000_com.syu.carlink.apk | `com.syu.carlink` 2.23.0718 | 〃 |
| CARLINK 2.25.0325.1438 | 폴더 내 190000000_com.syu.carlink.apk | `com.syu.carlink` 2.25.0325.1438 | 〃 (최신). sharedUserId `android.uid.systemui` |
| USB to Dual AVI Output Dongle | MS9120_...apk | `com.ms.ms2160` | USB→AV 출력 동글(MS9120) 제어 — 뒷좌석 모니터 등 |
| Car Logo Not Opening Fix | 190001002_com.syu.carmark logo.apk | `com.syu.carmark` "CarMark Settings" (system uid) | 부팅 차량 로고 설정 앱 수정판 |
| Firmware Forced Reset File | updatecfg.txt.zip | — | 펌웨어 강제 재설치용 설정 파일 (USB) |
| Front Sensor Upgrade File | T12_V3.1.Bin | — | 전방 주차 센서 펌웨어 |

CarLink 2.x 는 모두 `libAirPlay.so`, `libcarplayplugin_*.so`, `libcps_*.so`(CarPlay), `libautoreceiver_jni.so`(Android Auto), 그리고 `libwebrtc_audio_preprocessing.so`, `libblinkAEC.so`, `liblsaudioprocess.so`, `liblsecaudio.so`(통화 음성 처리)를 포함한다.
