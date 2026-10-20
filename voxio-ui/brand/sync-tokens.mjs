#!/usr/bin/env node
/**
 * 阶段8 功能点2/5/46/47：把 voxio-ui/brand/tokens.json 这一唯一 token 来源分发到三端，
 * 并把 brand/ 下的 Logo 资源同步到各端目录。
 *
 *   node voxio-ui/brand/sync-tokens.mjs          # 生成/覆盖
 *   node voxio-ui/brand/sync-tokens.mjs --check  # 只校验是否漂移（验收脚本用，漂移退出码 1）
 */
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(dirname(dirname(fileURLToPath(import.meta.url))));
const BRAND = join(ROOT, 'voxio-ui', 'brand');
const tokens = JSON.parse(readFileSync(join(BRAND, 'tokens.json'), 'utf8'));
const check = process.argv.includes('--check');

const NOTE = '自动生成，禁止手工修改。来源 voxio-ui/brand/tokens.json，改完执行 node voxio-ui/brand/sync-tokens.mjs';

function buildCss() {
  const sec = (title, lines) => ['', `  /* ${title} */`, ...lines];
  return [
    `/* ${NOTE} */`,
    ':root {',
    ...sec('颜色', Object.entries(tokens.color).map(([k, v]) => `  --sb-color-${k}: ${v};`)),
    ...sec('圆角', Object.entries(tokens.radius).map(([k, v]) => `  --sb-radius-${k}: ${v};`)),
    ...sec('阴影', Object.entries(tokens.shadow).map(([k, v]) => `  --sb-shadow-${k}: ${v};`)),
    ...sec(`间距 ${tokens.space.join('/')}`, tokens.space.map((v, i) => `  --sb-space-${i + 1}: ${v};`)),
    ...sec('字体', Object.entries(tokens.font).map(([k, v]) => `  --sb-font-${k}: ${v};`)),
    ...sec('组件尺寸', Object.entries(tokens.component).map(([k, v]) => `  --sb-${k}: ${v};`)),
    ...sec('品牌', Object.entries(tokens.brand).map(([k, v]) => `  --sb-brand-${k}: ${k === 'mark' ? v : `"${v}"`};`)),
    ...sec('动效（时长 150/200/250ms 三档，缓动只用这三条曲线）',
      Object.entries(tokens.motion).map(([k, v]) => `  --sb-motion-${k}: ${v};`)),
    '',
    '  /* Element Plus 变量映射（管理后台与访客端均使用 Element Plus） */',
    '  --el-color-primary: var(--sb-color-primary);',
    '  --el-color-success: var(--sb-color-success);',
    '  --el-color-warning: var(--sb-color-warning);',
    '  --el-color-danger: var(--sb-color-danger);',
    '  --el-color-error: var(--sb-color-danger);',
    '  --el-color-info: var(--sb-color-info);',
    '  --el-border-radius-base: var(--sb-radius-sm);',
    '  --el-font-family: var(--sb-font-family);',
    '  --el-text-color-primary: var(--sb-color-text);',
    '  --el-text-color-regular: var(--sb-color-text-secondary);',
    '  --el-text-color-secondary: var(--sb-color-text-muted);',
    '  --el-border-color: var(--sb-color-border);',
    '  --el-border-color-light: var(--sb-color-border);',
    '  --el-border-color-lighter: var(--sb-color-border);',
    '  --el-bg-color-page: var(--sb-color-bg-page);',
    '  --el-fill-color-light: var(--sb-color-bg-table-header);',
    '}',
    '',
  ].join('\n');
}

function buildScss() {
  const kv = (prefix, map) => Object.entries(map).map(([k, v]) => `$${prefix}-${k}: ${v} !default;`);
  return [
    `// ${NOTE}`,
    `$sb-version: "${tokens.version}";`,
    '',
    '// 颜色',
    ...kv('sb-color', tokens.color),
    '',
    '// 圆角',
    ...kv('sb-radius', tokens.radius),
    '',
    '// 阴影',
    ...kv('sb-shadow', tokens.shadow),
    '',
    '// 间距',
    ...tokens.space.map((v, i) => `$sb-space-${i + 1}: ${v} !default;`),
    '',
    '// 字体',
    ...kv('sb-font', tokens.font),
    '',
    '// 组件尺寸',
    ...Object.entries(tokens.component).map(([k, v]) => `$sb-${k}: ${v} !default;`),
    '',
    '// 品牌',
    ...Object.entries(tokens.brand).map(([k, v]) => `$sb-brand-${k}: ${k === 'mark' ? v : `"${v}"`} !default;`),
    '',
    '// 动效',
    ...Object.entries(tokens.motion).map(([k, v]) => `$sb-motion-${k}: ${v} !default;`),
    '',
  ].join('\n');
}



const CSS = buildCss();
const SCSS = buildScss();

/** 生成文件：目标 -> 内容 */
const GENERATED = new Map([
  ['voxio-ui/brand/tokens.css', CSS],
  ['voxio-ui/src/assets/styles/tokens.css', CSS],
  ['voxio-cs-user-ui/src/styles/tokens.css', CSS],
  ['voxio-ui/src/assets/styles/tokens.scss', SCSS],
]);

/**
 * 资产复制：源 -> 目的列表。
 * 源只有 brand/ 这一处；各端 public 与 Assets 下的同名文件都是这里的产物，手工改会在 --check 时报漂移。
 * 落位尺寸口径（见 brand/brand-guide.md）：favicon 16/32、侧栏与窗口 18-40、登录页横版 120-160、Hero 200-320。
 */
const ASSETS = [
  // 站点图标：favicon.svg 是「声桥」的加粗光学版，三块齐全但相对厚度加大，16px 下才不糊
  ['voxio-ui/brand/favicon.svg', [
    'voxio-ui/public/favicon.svg',
    'voxio-cs-user-ui/public/favicon.svg',
  ]],
  ['voxio-ui/brand/ico/favicon.ico', [
    'voxio-ui/public/favicon.ico',
    'voxio-cs-user-ui/public/favicon.ico',
  ]],
  // 主标记（单色黑）：管理后台品牌区、访客端聊天头部、官网页头页脚
  ['voxio-ui/brand/logo-primary.svg', [
    'voxio-ui/src/assets/logo/logo.svg',
    'voxio-cs-user-ui/src/assets/brand/mark.svg',
  ]],
  // 反白标记：深色底（管理后台深色侧栏主题）。访客端没有深色品牌区，所以不再往它那里发。
  ['voxio-ui/brand/logo-primary-white.svg', [
    'voxio-ui/src/assets/logo/logo-inverse.svg',
  ]],
  // 横版组合：管理后台登录页与官网首屏品牌位用 SVG，位图产物留给需要位图的场景
  ['voxio-ui/brand/logo-horizontal.svg', [
    'voxio-ui/src/assets/logo/logo-horizontal.svg',
  ]],
  ['voxio-ui/brand/logo-horizontal-white.svg', [
    'voxio-ui/src/assets/logo/logo-horizontal-white.svg',
    // 加载闪屏在 index.html 里，拿不到 src/assets，只能走 public 直链
    'voxio-ui/public/logo-horizontal-white.svg',
  ]],
  // 位图兜底：管理后台侧栏用 img 引 png（SVG 在部分场景拿不到尺寸自适应）
  ['voxio-ui/brand/png/mark-64.png', ['voxio-ui/src/assets/logo/logo.png']],
  // 分享图：官网 og:image 用位图（社交抓取器多数不解析 SVG）
];

const sha = (buf) => createHash('sha256').update(buf).digest('hex').slice(0, 12);

let changed = 0;
let drift = 0;

function put(relPath, buf) {
  const abs = join(ROOT, relPath);
  const cur = existsSync(abs) ? readFileSync(abs) : null;
  if (cur && sha(cur) === sha(buf)) return;
  if (check) {
    drift++;
    console.log(`DRIFT  ${relative(ROOT, abs)}`);
    return;
  }
  mkdirSync(dirname(abs), { recursive: true });
  writeFileSync(abs, buf);
  changed++;
  console.log(`WRITE  ${relative(ROOT, abs)}`);
}

for (const [rel, content] of GENERATED) put(rel, Buffer.from(content, 'utf8'));
for (const [src, dests] of ASSETS) {
  const absSrc = join(ROOT, src);
  if (!existsSync(absSrc)) {
    console.error(`缺失源文件 ${src}，请先执行 node voxio-ui/brand/build-brand.mjs`);
    process.exit(1);
  }
  const buf = readFileSync(absSrc);
  for (const dest of dests) put(dest, buf);
}

if (check) {
  console.log(drift ? `\ntoken/资产漂移 ${drift} 处，请执行 node voxio-ui/brand/sync-tokens.mjs`
    : `\n无漂移：${GENERATED.size} 个生成文件 + ${ASSETS.reduce((n, [, d]) => n + d.length, 0)} 个资产副本一致`);
  process.exit(drift ? 1 : 0);
}
console.log(`\n完成：${changed} 个文件写入`);
