#!/usr/bin/env bash
# ============================================================================
#  把皮肤目录打包成可导入的皮肤包（skin.json 必须在 zip 根目录）
#
#  用法：bash tools/pack-skin.sh <皮肤目录> [输出.zip]
#  例：  bash tools/pack-skin.sh ../my_skin        → my_skin.zip
#
#  为什么需要它：解压后 `skin.json` 必须直接躺在根目录（或唯一的一级子目录里），
#  否则「导入」会报"皮肤包缺少 skin.json"。手动右键压缩很容易多套一层目录。
# ============================================================================
set -euo pipefail

SRC="${1:?用法: bash tools/pack-skin.sh <皮肤目录> [输出.zip]}"
[ -d "$SRC" ] || { echo "❌ 目录不存在: $SRC" >&2; exit 1; }
[ -f "$SRC/skin.json" ] || { echo "❌ $SRC 里没有 skin.json" >&2; exit 1; }

OUT="${2:-$(basename "$(cd "$SRC" && pwd)").zip}"
case "$OUT" in /*) ;; *) OUT="$(pwd)/$OUT" ;; esac
rm -f "$OUT"

python3 - "$SRC" "$OUT" <<'PY'
import json, os, sys, zipfile
src, out = sys.argv[1], sys.argv[2]
spec = json.load(open(os.path.join(src, "skin.json"), encoding="utf-8"))
SKIP = {"__MACOSX", ".DS_Store", "Thumbs.db", ".git", "build", ".gradle"}
n = 0
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, 9) as z:
    for dp, dn, fn in os.walk(src):
        dn[:] = [d for d in dn if d not in SKIP]
        for f in sorted(fn):
            if f in SKIP:
                continue
            full = os.path.join(dp, f)
            z.write(full, os.path.relpath(full, src))   # 相对皮肤目录 → skin.json 在根
            n += 1
print(f"  皮肤: {spec.get('name')}  (id={spec.get('id')})")
print(f"  打包: {out}")
print(f"  文件: {n} 个 / {round(os.path.getsize(out)/1024, 1)} KB")
PY
echo
echo "下一步：把 zip 传到手机 → App 里【歌词图片】页 → 皮肤栏最右的「导入」→ 选这个 zip"
echo "同 id 会直接覆盖旧版本，不用卸载；用户之前对这套皮肤的调整也会保留。"
