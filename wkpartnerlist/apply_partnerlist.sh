#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(pwd)}"
MODULE_SOURCE="$(cd "$(dirname "$0")" && pwd)/wkpartnerlist"

if [ ! -f "$ROOT/settings.gradle" ] || [ ! -f "$ROOT/app/build.gradle" ]; then
  echo "错误：请在唐僧叨叨 Android 项目根目录运行，或把项目根目录作为第一个参数。" >&2
  exit 1
fi

if [ ! -d "$MODULE_SOURCE" ]; then
  echo "错误：压缩包中缺少 wkpartnerlist 模块。" >&2
  exit 1
fi

rm -rf "$ROOT/wkpartnerlist"
cp -R "$MODULE_SOURCE" "$ROOT/wkpartnerlist"

python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])

settings = root / 'settings.gradle'
s = settings.read_text(encoding='utf-8')
if "include ':wkpartnerlist'" not in s:
    anchor = "include ':wkpartnerbrowse'"
    if anchor in s:
        s = s.replace(anchor, anchor + "\ninclude ':wkpartnerlist'", 1)
    else:
        s = s.rstrip() + "\ninclude ':wkpartnerlist'\n"
    settings.write_text(s, encoding='utf-8')

app_gradle = root / 'app' / 'build.gradle'
s = app_gradle.read_text(encoding='utf-8')
line = "    implementation project(path: ':wkpartnerlist')"
if "project(path: ':wkpartnerlist')" not in s and "project(':wkpartnerlist')" not in s:
    anchor = "    implementation project(path: ':wkpartnerbrowse')"
    if anchor in s:
        s = s.replace(anchor, anchor + "\n" + line, 1)
    else:
        idx = s.rfind('}')
        if idx < 0:
            raise SystemExit('app/build.gradle 中未找到 dependencies 结束位置')
        s = s[:idx] + line + "\n" + s[idx:]
    app_gradle.write_text(s, encoding='utf-8')

# 让底部“语伴”按钮默认打开独立列表模块。只改 TabActivity 的入口，保留 wkpartnerbrowse 模块。
tab_candidates = [
    root / 'wkuikit' / 'src' / 'main' / 'java' / 'com' / 'chat' / 'uikit' / 'TabActivity.java',
]
for tab in tab_candidates:
    if not tab.exists():
        continue
    s = tab.read_text(encoding='utf-8')
    old = s
    s = s.replace('EndpointManager.getInstance().invoke("peipe_open_partner_browse", this)',
                  'EndpointManager.getInstance().invoke("peipe_open_partner_list", this)')
    s = s.replace('Class.forName("com.chat.partnerbrowse.PartnerBrowseActivity")',
                  'Class.forName("com.chat.partnerlist.PartnerListActivity")')
    s = s.replace('语伴模块加载失败，请检查 wkpartnerbrowse 模块',
                  '语伴列表模块加载失败，请检查 wkpartnerlist 模块')
    if s != old:
        tab.write_text(s, encoding='utf-8')
    break
PY

echo "wkpartnerlist 已接入。建议执行：./gradlew :wkpartnerlist:assembleDebug :app:assembleDebug"
