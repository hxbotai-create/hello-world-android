#!/bin/bash
set -e
export JAVA_HOME=$HOME/jdk
export PATH=$JAVA_HOME/bin:$PATH
ANDROID_HOME=$HOME/android-sdk
BT=$ANDROID_HOME/build-tools/34.0.0
AJAR=$ANDROID_HOME/platforms/android-34/android.jar
PROJ=$HOME/workspace/hello-world-android
cd $PROJ
rm -rf build && mkdir -p build/gen build/classes build/dex
echo "1. aapt2 compile"
$BT/aapt2 compile --dir app/src/main/res -o build/compiled.zip
echo "2. aapt2 link"
$BT/aapt2 link -o build/app-unsigned.apk -I $AJAR --manifest app/src/main/AndroidManifest.xml -R build/compiled.zip --java build/gen --auto-add-overlay --min-sdk-version 24 --target-sdk-version 34
echo "3. javac"
find build/gen app/src/main/java -name "*.java" > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -cp $AJAR -d build/classes @build/sources.txt 2>&1 | grep -v "bootstrap\|deprecat" || true
ls build/classes/com/muse/helloworld/
echo "4. d8"
$BT/d8 --lib $AJAR --min-api 24 --output build/dex $(find build/classes -name "*.class")
ls -lh build/dex/
echo "5. add dex to apk"
cp build/app-unsigned.apk build/app-with-dex.apk
python3 -c "
import zipfile
with zipfile.ZipFile('build/app-with-dex.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    z.write('build/dex/classes.dex','classes.dex')
print('dex added')
"
echo "6. zipalign"
$BT/zipalign -f -p 4 build/app-with-dex.apk build/app-aligned.apk
echo "7. sign"
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US" 2>&1 | tail -5
fi
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out build/hello-world.apk build/app-aligned.apk
echo "8. verify"
$BT/apksigner verify --print-certs build/hello-world.apk
$BT/aapt2 dump badging build/hello-world.apk | head -n 20
ls -lh build/hello-world.apk
cp build/hello-world.apk $HOME/workspace/hello-world-android/hello-world.apk
cp build/hello-world.apk $HOME/workspace/your_files/hello-world.apk 2>/dev/null || mkdir -p $HOME/workspace/your_files && cp build/hello-world.apk $HOME/workspace/your_files/hello-world.apk
ls -lh $HOME/workspace/your_files/hello-world.apk
