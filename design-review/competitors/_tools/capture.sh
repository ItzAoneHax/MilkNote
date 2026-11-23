#!/usr/bin/env bash
# 用法: capture.sh <app_dir> <page_id> [device]
# 抓取当前屏幕 PNG 到 <app_dir>/screenshots/<page_id>.png，
# 并导出 UI Automator 层级到 <app_dir>/ui/<page_id>.xml
set -euo pipefail
APP_DIR="$1"; ID="$2"; DEV="${3:-emulator-5556}"
export PATH="$PATH:/d/Android/Sdk/platform-tools"
export MSYS_NO_PATHCONV=1   # 防止 Git Bash 把 /sdcard/... 转成 Windows 路径
mkdir -p "$APP_DIR/screenshots" "$APP_DIR/ui"
adb -s "$DEV" exec-out screencap -p > "$APP_DIR/screenshots/$ID.png"
for attempt in 1 2 3; do
  if adb -s "$DEV" shell uiautomator dump /sdcard/ui_dump.xml >/dev/null 2>&1; then
    adb -s "$DEV" pull /sdcard/ui_dump.xml "$APP_DIR/ui/$ID.xml" >/dev/null 2>&1 && break
  fi
  sleep 1
done
PYTHONIOENCODING=utf-8 python - "$APP_DIR/ui/$ID.xml" <<'PY' 2>/dev/null || true
import sys, re, xml.etree.ElementTree as ET
p = sys.argv[1]
try:
    root = ET.parse(p).getroot()
except Exception as e:
    print("ui parse failed:", e); sys.exit(0)
n = 0
for node in root.iter("node"):
    text = node.get("text") or ""; desc = node.get("content-desc") or ""
    rid = node.get("resource-id") or ""
    if text or desc:
        n += 1
        print(f'{node.get("class","").split(".")[-1]:14s} {node.get("bounds",""):26s} text={text[:50]!r} desc={desc[:40]!r} id={rid.split("/")[-1]}')
print(f"[{n} labelled nodes]")
PY
echo "saved: $APP_DIR/screenshots/$ID.png ($(stat -c %s "$APP_DIR/screenshots/$ID.png") bytes)"
