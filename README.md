# XposedTeyes
Framework modifications for Teyes CC3 2k through Xposed

## System Changes

1. Resizable Split Screen.
Will allow you to resize the splitscreen. You will need to activate module for Android Framework and also SystemUI.
2. Startup app (3.1, replaces "Autolaunch Split Screen")
Choose in the app (Startup app): when (full boot / ACC ON & wake up / both), which app (picked from a list), delay and retries, whether to launch only while the home screen is shown, and a separate app on Bluetooth connection. Reverse camera and calls keep their system priority. 3.0 auto launch settings are not carried over; set them again after updating. See docs/SPEC.md.
3. Hide Divider.
Show or hide the split screen divider. If hidden a simple 1 pixel black line will be shown instead.
4. Screen Ratio.
Define your prefered ratio for the split screen, for e.g. 0.5 for half, 0.33 for 1third and so on.
5. Left Screen Position.
If checked the smaller split screen (in case for e.g. if you set screen ratio to 0.33) will be on the right side instead of the left.
6. Launcher.
Two separate shortcuts to launch your specified applications (in settings for e.g. "com.spotify.music" and "com.waze" and "video.player.videoplayer") in split screen mode. For e.g. one for Audio / Navigation and one for Navigation / Videos (in case long road and children bored).
7. Bluetooth Launcher.
Launch an app when bluetooth connects (now under Startup app).
8. Ad Blocker.
Try to hide ads from applications. Raw method taken from [AdBlocker-Reborn](https://github.com/AdBlocker-Reborn/AdBlocker_Reborn/blob/master/app/src/main/assets/blocklist/av_aggressive)
9. Possibility to force SystemUI visibility globally.
10. Fixes.
Some fixes for apps like hide Waze floating car type button, color fixes for Audials and auto launch the now playing screen, possibility to define DPI for Mini Desktop launcher, rounded buttons for phone on screen layout, rounded corners for on screen volume slider, new car images for reversing and radar.

## Reverse Camera

1. Allow custom preview Width and Height
2. Possibility to set X and Y positions
3. Show warning message
4. Enhance preview so it doesn't look so pixelated on analog (original) car camera sources
5. Rounded corners around the preview so it matches android auto look
6. Keep SystemUI on screen so you will be able to interact with the statusbar

## CarLink

1. Custom video resolution support for Android Auto as default is very low and looks very pixelated.
2. Width and Height margin for the stream so you can move the streamed screen around (supports even negative numbers like -10 so that the small black borders are removed from the stream and the whole display is used)
3. Custom DPI so you can change the look of the Android Auto layout (Android Auto only, not CarPlay)
4. Developer Mode (which is automatically turned on by this app) so Android Auto will support all unofficial apps.
5. Keep SystemUI on screen so you will be able to interact with the statusbar without having to swipe, for e.g. change volume (also looks better IMO) 

## Status of the 3.1 additions

| Feature | Code | Tested on a real CC3 |
|---|---|---|
| Startup app / Bluetooth launch | implemented, unit tested | not yet |
| CarPlay UI size (smaller / larger than default, CarLink 2.22-2.25) | implemented, unit tested | not yet |
| CarPlay call microphone volume / noise suppression (root) | implemented | not yet |
| Normal Bluetooth call microphone | diagnostics only (docs/research/bt-mic.md) | - |

# Build

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

See docs/BUILD.md. A self-built APK is signed with a different key than the released 3.0 APK and cannot be installed over it.

# Install
Install instructions

1. Install Magisk on your Teyes Head Unit and set Magisk > Settings > enable Zygisk
2. Install LSPosed Zygisk module
3. Install latest XposedTeyes released apk
4. Activate XposedTeyes module in your LSPosed > Modules section
5, Check "Android Framework", "SystemUI", "CarLink", "UI Server", "Mini Desktop", "Waze", "MainServer", "Audials" and any other app to be used on

# Notes
Tested on Teyes CC3 2k and vanila Android 10 so think should work on other headunits too.
All changes except the Launcher and Startup app settings will require a restart of the device.

