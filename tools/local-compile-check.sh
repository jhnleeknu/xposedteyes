#!/bin/sh
# Fallback compile check for machines without the Android SDK / Google Maven
# (the real build is ./gradlew :app:assembleDebug, run by CI).
#
# Needs: aapt (Debian/Ubuntu package "aapt"), a JDK, and a framework jar that
# contains android.jar classes and resources.arsc, e.g. Robolectric's
#   org.robolectric:android-all:9-robolectric-4913185-2 (Maven Central).
# Usage: tools/local-compile-check.sh /path/to/android-all.jar
# Note: android-all also contains hidden APIs, so this check is looser than the
# real SDK; CI remains the reference.
set -eu
ANDROID_JAR=${1:?path to android-all / android.jar}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${TMPDIR:-/tmp}/xposedteyes-compile-check
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes"

# The Gradle build sets the package through `namespace`; aapt needs it in the manifest.
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.pijulius.xposedteyes"><uses-sdk android:minSdkVersion="28" android:targetSdkVersion="28" />|' \
	"$ROOT/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"

aapt package -f -m -J "$OUT/gen" -M "$OUT/AndroidManifest.xml" -S "$ROOT/res" -A "$ROOT/assets" \
	-I "$ANDROID_JAR" -F "$OUT/res.apk"

cat > "$OUT/gen/BuildConfig.java" <<'JAVA'
package com.pijulius.xposedteyes;
public final class BuildConfig {
	public static final String VERSION_NAME = "local";
	public static final String GIT_SHA = "local";
}
JAVA

javac -Xlint:deprecation -Xlint:unchecked -nowarn -source 8 -target 8 -encoding UTF-8 -d "$OUT/classes" \
	-cp "$ANDROID_JAR:$ROOT/lib/api-82.jar" \
	$(find "$ROOT/src" "$ROOT/core/src/main/java" "$OUT/gen" -name '*.java')
echo "compile check OK"
