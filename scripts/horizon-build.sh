#!/bin/bash
# Builds launcher/out/SteroidHome.apk (Horizon) with plain javac/d8/aapt2 — no Gradle.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"; export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"; JAR="$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar"
L=launcher; O=$L/out
mkdir -p $L/assets/config; cp config/{apps,stations,windows,features,sky,festivals}.json $L/assets/config/
[ -n "$(ls $L/assets/windows 2>/dev/null)" ] || scripts/fetch-previews.sh >/dev/null  # World's Windows thumbnails
KS=~/.android/debug.keystore
[ -f $KS ] || { mkdir -p ~/.android; keytool -genkeypair -keystore $KS -storepass android -keypass android -alias androiddebugkey \
  -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1; }
rm -rf $O; mkdir -p $O/classes $O/dex
javac --release 8 -Xlint:none -classpath "$JAR" -d $O/classes $(find $L/src -name '*.java') 2>&1 | grep -v "^Note:" || true
[ -n "$(find $O/classes -name '*.class')" ] || { echo "COMPILE FAILED"; exit 1; }
"$BT/d8" --min-api 26 --lib "$JAR" --output $O/dex $(find $O/classes -name '*.class')
"$BT/aapt2" compile --dir $L/res -o $O/res.zip
"$BT/aapt2" link -I "$JAR" --manifest $L/AndroidManifest.xml -A $L/assets $O/res.zip -o $O/base.apk
(cd $O && zip -qj base.apk dex/classes.dex)
"$BT/zipalign" -f 4 $O/base.apk $O/aligned.apk
"$BT/apksigner" sign --ks $KS --ks-pass pass:android --out $O/SteroidHome.apk $O/aligned.apk
echo "BUILT $O/SteroidHome.apk"
