#!/usr/bin/env python3
"""
皮肤自检：交付前跑一遍，保证「导入」不会失败、面板不会出现奇怪控件。

用法：
    python3 tools/check-skin.py <皮肤目录 或 skin.json 路径>

检查项：JSON 合法性 / id 合法性与唯一性 / 元素类型 / 控件表拼写与取值 /
颜色写法 / 区间顺序 / 资源文件是否存在 / 设计规范告警。
退出码 0 = 通过（可能有告警），1 = 有错误（必须改）。
"""
import json
import os
import re
import sys

# ---- 引擎认识的东西（照 docs/皮肤制作指南.md §4） ----
ELEMENT_TYPES = {"text", "lyric", "image", "qrcode", "divider", "spacer", "group"}
PROPERTIES = {"visible", "source", "shape", "scale", "offset_x", "offset_y",
              "placement", "color", "background", "font", "text"}
CONTROLS = {"switch", "slider", "color", "enum", "font", "image", "shape", "text"}
# 控件缺省 kind 时的推断（引擎侧 ControlKind.forProperty）
KIND_OF = {"visible": "switch", "source": "image", "shape": "shape", "scale": "slider",
           "offset_x": "slider", "offset_y": "slider", "placement": "enum",
           "color": "color", "background": "color", "font": "font", "text": "text"}
# 元素类型 → 引擎支持的属性（声明别的会被静默忽略）
# 元素类型 → 引擎**实际响应**的属性（与 render/LyricImageRenderer 的行为对齐）：
# 显隐/缩放/位移/锚点/底色 对任何元素都通用；颜色、换源、形状、字体/文字 各有适用范围。
_GENERIC = {"visible", "scale", "offset_x", "offset_y", "placement", "background"}
SUPPORTED = {
    "image": _GENERIC | {"source", "shape", "color"},
    "text": _GENERIC | {"color", "font", "text"},
    "lyric": _GENERIC | {"color", "font", "text"},
    "qrcode": _GENERIC | {"color", "source"},
    "divider": _GENERIC | {"color"},
    "spacer": _GENERIC,
    "group": _GENERIC | {"shape"},
}
SHAPES = {"rounded", "rect", "circle", "default"}
PLACEMENTS = {"skin", "left", "bottom", "right", "top", "center",
              "bottom_left", "bottom_center", "bottom_right", "top_left", "top_center", "top_right"}
COLOR_RE = re.compile(r"^#[0-9A-Fa-f]{6}$")
ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")

errors, warnings = [], []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def check_color(node, where):
    if node is None:
        return
    if isinstance(node, str):
        if not COLOR_RE.match(node):
            err(f"{where}: 颜色只能写 #RRGGBB，收到 {node!r}"
                + ("（带 alpha 的 #AARRGGBB 已不支持，请用 opacity）" if re.match(r"^#[0-9A-Fa-f]{8}$", node) else ""))
    elif isinstance(node, dict):
        if "color" in node:
            check_color(node["color"], where + ".color")
        op = node.get("opacity")
        if op is not None and not (isinstance(op, (int, float)) and 0 <= op <= 1):
            err(f"{where}.opacity 要在 0~1")
    else:
        err(f"{where}: 颜色写法不认识（应为 #RRGGBB 或 {{\"color\",\"opacity\"}}）")


def check_controls(ed, where, el_type, ids, order_seen):
    if not isinstance(ed, dict):
        err(f"{where}.editable 必须是对象（{{id, order, label, controls:[…]}}）；旧式开关写法已删除")
        return
    eid = ed.get("id")
    if eid is not None:
        if not ID_RE.match(str(eid)):
            err(f"{where}.editable.id 非法（只允许字母数字_-，≤64）：{eid!r}")
        elif eid in ids:
            err(f"{where}.editable.id 重复：{eid!r}（每个可编辑项的 id 必须唯一）")
        else:
            ids.add(eid)
    controls = ed.get("controls")
    if not isinstance(controls, list) or not controls:
        err(f"{where}.editable.controls 必须是非空数组（没有可调项就别声明 editable）")
        return
    for i, c in enumerate(controls):
        w = f"{where}.controls[{i}]"
        if not isinstance(c, dict):
            err(f"{w} 必须是对象"); continue
        prop = c.get("property")
        if prop not in PROPERTIES:
            err(f"{w}.property 不认识：{prop!r}（可选：{sorted(PROPERTIES)}）"); continue
        kind = c.get("control") or KIND_OF.get(prop)
        if kind not in CONTROLS:
            err(f"{w}.control 不认识：{c.get('control')!r}（可选：{sorted(CONTROLS)}）")
        if prop not in SUPPORTED.get(el_type, set()):
            warn(f"{w}: {el_type} 类型不响应 property={prop}（引擎会忽略它）")
        rng = c.get("range")
        if rng is not None:
            if (not isinstance(rng, list) or len(rng) < 2
                    or not all(isinstance(x, (int, float)) for x in rng[:2])):
                err(f"{w}.range 必须是两个数字 [min,max]")
            elif rng[0] >= rng[1]:
                err(f"{w}.range 顺序不对：{rng}（要 min < max）")
        elif kind == "slider":
            warn(f"{w}: slider 没写 range，面板会用兜底区间（位置类为 ±25%）")
        if kind == "enum":
            opts = c.get("options")
            if not isinstance(opts, list) or not opts:
                err(f"{w}.options 必须是非空数组（enum 控件）")
            elif prop == "placement":
                for o in opts:
                    if str(o).lower() not in PLACEMENTS:
                        err(f"{w}.options 里有不认识的位置：{o!r}")
        if kind == "shape":
            for o in (c.get("options") or []):
                if str(o).lower() not in SHAPES:
                    err(f"{w}.options 里有不认识的形状：{o!r}（rounded/rect/circle）")
        if kind == "color":
            pass
        if kind == "text":
            for k in ("minLines", "maxLines"):
                v = c.get(k)
                if v is not None and (not isinstance(v, int) or v < 1):
                    err(f"{w}.{k} 要是 ≥1 的整数")


def check_elements(els, where, skin_dir, ids):
    if not isinstance(els, list):
        err(f"{where} 必须是数组"); return 0
    n = 0
    for i, e in enumerate(els):
        w = f"{where}[{i}]"
        if not isinstance(e, dict):
            err(f"{w} 必须是对象"); continue
        t = e.get("type", "text")
        if t not in ELEMENT_TYPES:
            err(f"{w}.type 不认识：{t!r}（可选：{sorted(ELEMENT_TYPES)}）"); continue
        if "maxLines" in e and t in ("lyric", "text") and e["maxLines"]:
            warn(f"{w}: 声明了 maxLines={e['maxLines']}，超出会被省略号截断（不想要就删掉）")
        for k in ("color", "scrim"):
            check_color(e.get(k), f"{w}.{k}")
        if e.get("shadow"):
            check_color(e["shadow"].get("color"), f"{w}.shadow.color")
        if e.get("qr"):
            check_color(e["qr"].get("backgroundColor"), f"{w}.qr.backgroundColor")
        for k in ("src", "logo"):
            v = e.get(k)
            if isinstance(v, str) and v and not os.path.isfile(os.path.join(skin_dir, v)):
                warn(f"{w}.{k} 指向的文件不存在：{v}")
        f = e.get("font")
        if isinstance(f, dict) and f.get("source") in ("assets", "ASSETS") and f.get("value"):
            if not os.path.isfile(os.path.join(skin_dir, str(f["value"]))):
                warn(f"{w}.font 指向的文件不存在：{f['value']}")
        if "editable" in e:
            check_controls(e["editable"], w, t, ids, None)
        n += 1
        if e.get("children") is not None:
            if t != "group":
                err(f"{w}: 只有 group 能有 children")
            n += check_elements(e["children"], w + ".children", skin_dir, ids)
    return n


def main():
    target = sys.argv[1] if len(sys.argv) > 1 else "."
    skin_dir = target if os.path.isdir(target) else os.path.dirname(os.path.abspath(target))
    json_path = os.path.join(target, "skin.json") if os.path.isdir(target) else target
    if not os.path.isfile(json_path):
        print(f"❌ 找不到 skin.json：{json_path}"); return 1
    try:
        spec = json.load(open(json_path, encoding="utf-8"))
    except Exception as ex:
        print(f"❌ skin.json 不是合法 JSON：{ex}"); return 1

    sid = spec.get("id")
    if not isinstance(sid, str) or not ID_RE.match(sid):
        err(f"id 非法（字母数字_-，≤64）：{sid!r}")
    if not spec.get("name"):
        warn("没写 name，面板里会显示 id")
    canvas = spec.get("canvas") or {}
    if not isinstance(canvas.get("width"), int) or canvas.get("width", 0) <= 0:
        err("canvas.width 必须是正整数（参考画布宽度，内置皮肤用 1080）")
    elif canvas["width"] != 1080:
        warn(f"canvas.width = {canvas['width']}（建议 1080，与内置皮肤一致）")
    if isinstance(canvas.get("minHeight"), int) and canvas["minHeight"] > 0:
        warn("canvas.minHeight 设了值：歌词很少时会被撑成一张大卡片，一般不要写")
    if canvas.get("height"):
        warn("canvas.height 是固定高度：内容多少都不会变，一般不要写")
    check_color(canvas.get("background", {}).get("color"), "canvas.background.color")
    check_color((canvas.get("background") or {}).get("scrim"), "canvas.background.scrim")
    check_color((spec.get("defaults") or {}).get("color"), "defaults.color")

    ids = set()
    count = check_elements(spec.get("elements") or [], "elements", skin_dir, ids)
    bg = (canvas.get("background") or {}).get("editable")
    if bg is not None:
        check_controls(bg, "canvas.background", "image", ids, None)
    df = (spec.get("defaults") or {}).get("editable")
    if df is not None:
        check_controls(df, "defaults", "text", ids, None)

    print(f"皮肤: {spec.get('name')}  (id={sid})   元素 {count} 个，可编辑项 {len(ids)} 个")
    for w in warnings:
        print(f"  ⚠️  {w}")
    for e in errors:
        print(f"  ❌ {e}")
    print()
    if errors:
        print(f"有 {len(errors)} 处错误，必须改完再交付 ❌"); return 1
    print("通过 ✅" + (f"（{len(warnings)} 条告警可自行取舍）" if warnings else ""))
    print("下一步：bash tools/pack-skin.sh " + skin_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main())
