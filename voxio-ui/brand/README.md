# VOXIO 品牌资源（各端唯一来源）

这里是各端唯一的品牌与视觉来源：**改这里 → 跑两条命令 → 各端同步**。
规范正文见 [brand-guide.md](brand-guide.md)（品牌故事、构成、安全空间、最小尺寸、错误用法、色彩、字体、背景）。

## 1. 目录

```
brand/
  tokens.json          设计 token 唯一来源（颜色/圆角/阴影/间距/字体/组件尺寸/品牌文案/动效/状态）
  tokens.css           生成物：CSS 自定义属性（--sb-*）
  brand-guide.md       品牌规范
  logo-primary.svg         主标记（单色黑）
  logo-primary-white.svg   主标记反白
  logo-monochrome.svg      纯单色版（fill=currentColor）
  logo-horizontal.svg      横版组合 + 反白
  logo-vertical.svg        竖版组合 + 反白
  favicon.svg              「声桥」加粗光学版（三块齐全、斜杠更厚），16px 可用
  app-icon.svg             圆角底 + 反白标记，应用图标
  og-image.svg             1200×630 社交分享图
  png/                 位图产物（favicon 兜底、侧栏 img、分享图用）
  ico/                 favicon.ico（16/32/48）与 app-icon.ico（16/32/48/64/128/256）
  build-brand.mjs      SVG → PNG + ICO
  sync-tokens.mjs      tokens.json → CSS/SCSS；brand 产物 → 各端落位；--check 查漂移
```

命名规则：`logo-<形态>[-<变体>]`、`mark-<尺寸>`、`favicon-<尺寸>`、`app-icon-<尺寸>`。

## 2. 两条命令

```bash
node voxio-ui/brand/build-brand.mjs      # 光栅化：SVG → png/ 与 ico/（需本机 Chrome）
node voxio-ui/brand/sync-tokens.mjs      # 分发：tokens.json + brand 产物 → 各端
node voxio-ui/brand/sync-tokens.mjs --check   # 只校验漂移，漂移退出码 1（验收脚本用）
```

只改了颜色/尺寸/文案不用重跑 `build-brand.mjs`（位图里没有颜色以外的信息）；
**改了任何 SVG 就必须两条都跑**，否则各端拿到的还是旧位图。

## 3. token 与资产落位

| 生成物 | 使用方 | 引用方式 |
| --- | --- | --- |
| `src/assets/styles/tokens.{css,scss}` | 管理后台 | `main.js` 引 css，`index.scss` 首行 `@use` |
| `voxio-cs-user-ui/src/styles/tokens.css` | 访客端 | `main.js` 中在 `base.css` 前引入 |
| `public/favicon.{svg,ico}` | 管理后台 / 访客端 / 官网 | `index.html` `<link rel=icon>` |
| `src/assets/logo/logo*.svg`、`logo.png` | 管理后台 | 侧栏 `Logo.vue`（按深浅主题切黑/反白）、登录页横版 |
| `src/assets/brand/mark.svg` | 访客端 | 聊天窗口头部 |
| `public/{logo,logo-horizontal}.svg`、`og-image.png` | 官网 | 页头页脚、首屏品牌位、`og:image` |

CSS 变量命名 `--sb-<类别>-<名称>`，SCSS 同名 `$sb-*`，两边都由 `tokens.json` 生成。

## 4. 硬约束（违反即重做）

- 主标记是纯几何构成：**不画**气泡/耳机/电话/麦克风/笑脸/人像/对勾/盾牌/锁/齿轮，
  **不画**正弦波/条形音柱/同心圆波纹，不把六个字母排一行当 Logo。
- 必须可单色（`logo-monochrome.svg` 就是这条的自检文件）、必须 16px 清晰、1024px 不糊。
- 颜色只有一个来源：`tokens.json`。任何端里再出现十六进制主色，`sync-tokens.mjs --check` 之外还要靠 grep 门禁拦。
- 品牌名两种写法不互换：标志内 `VOXIO`（全大写），正文里 `Voxio`。
