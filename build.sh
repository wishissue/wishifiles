#!/bin/bash
# Wishi Files build: aapt + javac + dx + zipalign + apksigner (no Gradle needed; see README)
set -e
cd "$(dirname "$0")"
AJ=/usr/lib/android-sdk/platforms/android-23/android.jar
B=build; rm -rf $B; mkdir -p $B/gen $B/classes
aapt package -f -M AndroidManifest.xml -S res -A assets -I $AJ -J $B/gen -F $B/base.apk --min-sdk-version 23 --target-sdk-version 34 --version-code 7 --version-name 1.4.0
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath $AJ -d $B/classes $(find $B/gen -name R.java) src/org/wishy/files/*.java
dalvik-exchange --dex --min-sdk-version=23 --output=$B/classes.dex $B/classes
python3 tools/patch_manifest.py $B/base.apk $B/classes.dex $B/unsigned.apk
zipalign -f -p 4 $B/unsigned.apk $B/aligned.apk
[ -f tools/release.keystore ] || keytool -genkeypair -keystore tools/release.keystore -storepass wishi123 -keypass wishi123 -alias wishi -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Wishi Files, O=wishissue" >/dev/null 2>&1
apksigner sign --ks tools/release.keystore --ks-pass pass:wishi123 --key-pass pass:wishi123 --out $B/WishiFiles.apk $B/aligned.apk
apksigner verify --verbose $B/WishiFiles.apk | head -6
ls -l $B/WishiFiles.apk | awk '{print "APK bytes:",$5}'
