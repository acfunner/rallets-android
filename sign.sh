#!/bin/bash - 
set -e
VERSION=$1
if [ "$#" -eq 1 ]; then
  jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore .keystore ./target/android/output/Rallets-release-unsigned.apk rallets
  zipalign -p 4 ./target/android/output/Rallets-release-unsigned.apk ~/Desktop/Rallets-$VERSION.apk
else
  echo "请提供版本号"
fi
