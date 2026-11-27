#!/bin/bash
# Usage: tap.sh "visible text" [scroll-tries]  -- taps the centre of the first node whose text matches
export MSYS_NO_PATHCONV=1
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
want="$1"; tries="${2:-1}"
for i in $(seq 1 "$tries"); do
  "$A" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  b=$("$A" shell cat /sdcard/ui.xml | tr '>' '\n' | grep -F "text=\"$want\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"')
  if [ -n "$b" ]; then
    read x1 y1 x2 y2 <<< $(echo "$b" | grep -oE '[0-9]+' | tr '\n' ' ')
    "$A" shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 ))
    echo "tapped $want"
    exit 0
  fi
  "$A" shell input swipe 540 1800 540 900 300
  sleep 1
done
echo "NOT FOUND: $want"
exit 1
