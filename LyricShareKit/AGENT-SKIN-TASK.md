# 任务书：做一套 LyricShareKit 皮肤包（交付 zip 给用户）

> 你要交付的**唯一产物**是：一个**能直接导入的皮肤 zip**（`<皮肤目录名>.zip`），外加一段说明。
> **不需要改任何代码、不需要重新编译**（皮肤是纯数据，运行时导入）。

## 一、先读这几处（都在 LyricShareKit/ 里）

| 位置 | 看什么 |
|---|---|
| `docs/皮肤制作指南.md` | §4 字段速查（canvas/defaults/元素/颜色）、**§4.4.2 控件表**、§4.4.2.1 文字参数、§4.4.2.2 逐对象字体、§4.4.3 组合 `group`、**§4.5 打包与导入**、§11 调试清单 |
| `src/main/assets/skins/{default_light,dark_card,film_black}/skin.json` | 三套现成皮肤，**直接拿最接近的一套当模板改**（比从零写快、也不容易漏字段） |
| `README.md` | 能力总览 |

## 二、步骤

1. **选模板**：把最接近你目标效果的一套内置皮肤**整个目录复制**到工作区（例如 `work/my_skin/`）。
2. **改 `skin.json`**：
   - `id`：改成新的（只允许 `A-Za-z0-9_-`，≤64；**不要和内置的 id 重名**，除非你就是想覆盖它）；
   - `name`：面板里显示的中文名；
   - `canvas.width` 保持 **1080**（参考画布宽度，所有尺寸都按它写）；
   - 按需求改 `elements`（元素顺序 = 从上到下的排版顺序）；
   - 想让用户在面板里能调什么，就在对应元素/`canvas.background`/`defaults` 上写 `editable.controls`；**不写就没有入口**。
3. **自检**（必做，0 error 才继续）：
   ```bash
   python3 tools/check-skin.py work/my_skin
   ```
   它会检查 JSON 合法性、id、元素类型、控件拼写与取值、颜色写法、区间顺序、资源文件是否存在，并给出设计告警。
4. **打包**：
   ```bash
   bash tools/pack-skin.sh work/my_skin          # → work/my_skin.zip
   ```
   （脚本保证 `skin.json` 在 zip 根目录 —— 结构错了 App 会报"皮肤包缺少 skin.json"）
5. **交付给用户**：zip 的**绝对路径** + 一段说明：
   ```
   皮肤名 / id：
   改了什么（版式、配色、字号、编辑项）：
   用户在面板里可以调什么：
   需要用户自己补的（例如某张图、某个字体）：
   ```

## 三、硬约束（踩了就是白做）

1. **颜色只写 `#RRGGBB`**；透明度用同级独立的 `opacity: 0~1`。`#AARRGGBB` 已不支持。
2. **可编辑项只认这种写法**（旧式开关 `replace/visibility/size/move/...` 与 `"editable": true` **已删除**）：
   ```jsonc
   "editable": { "id": "qr", "order": 4, "label": "二维码",
     "controls": [ { "property": "visible", "control": "switch", "label": "显示二维码" } ] }
   ```
   - `property` 可选：`visible source shape scale offset_x offset_y placement color background font text`
   - `control` 可选：`switch slider color enum font image shape text`（不写会按 property 推断）
   - `slider` 要写 `range:[min,max]`，**颗粒度**写 `step`（不写按 1%）
   - `enum` 要写 `options`（如 `["left","bottom","right"]`）
   - **每个 `editable.id` 必须唯一**；`order` 决定面板里的先后
3. **歌词 / 标题不要写 `maxLines`**（会把内容截断成 `…`）；要让整张海报随内容变高就什么都别写。
4. **不要写 `canvas.minHeight` / `canvas.height`**（会让"歌词少的时候也是大卡片"）。
5. 文字里可以用占位符与时间参数：`{title} {artists} {album} {lines} {firstLine} {count} {brand}`、
   `YYYY YY MM dd HH hh mm ss`、`{date} {time}`（用到时间参数时预览会每秒刷新）。
6. **不要改模块代码**（`src/main/java/**`）、**不要改内置皮肤**（`assets/skins/**`）—— 那才需要重新编译。
7. 皮肤包里除了 `skin.json`，只放它用到的**字体/图片**（路径相对皮肤目录）。

## 四、完成标准（DoD）

- [ ] `python3 tools/check-skin.py <目录>` → **通过 ✅，0 error**（告警可自行取舍，但要在交付说明里讲清）
- [ ] `bash tools/pack-skin.sh <目录>` 成功，zip 根目录能看到 `skin.json`
- [ ] 交付说明写全（皮肤名/id、改了什么、用户能调什么、需要用户补什么）
- [ ] 没有改动任何代码或内置皮肤

## 五、常见坑

| 现象 | 原因 |
|---|---|
| App 报"皮肤包缺少 skin.json" | zip 里多套了一层目录（要把 `skin.json` 压到根） |
| 面板里没有你想要的入口 | 该元素/背景/`defaults` 上没写 `editable.controls` |
| 声明的控件点了没反应 | `property` 与元素类型不匹配（跑 `check-skin.py` 会告警） |
| 颜色不生效 | 写成了 `#AARRGGBB`，或忘了用独立 `opacity` |
| 海报上内容被截断 | 某处写了 `maxLines` |
