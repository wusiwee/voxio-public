#!/usr/bin/env node
/**
 * VOXIO 品牌资产构建：读 voxio-ui/brand/*.svg → 生成多尺寸 PNG 与 ICO，落到 brand/png 与 brand/ico。
 *
 *   node voxio-ui/brand/build-brand.mjs              生成全部
 *   node voxio-ui/brand/build-brand.mjs --only=ico   只重做图标包
 *
 * 设计口径（改图前先读）：
 * - 主标记「声桥」是纯几何构成：两个等大方形节点 + 一条 45° 斜杠，整体绕画布中心 180° 旋转对称。
 *   节点是对等的两端（访客/客服），斜杠是它们之间唯一的通道；不画气泡/耳机/麦克风/波形/字母排，
 *   也不依赖颜色 —— logo-monochrome.svg 就是这条约束的自检文件。
 * - favicon 用同一构成的加粗光学版：三块齐全（删块就不是同一个标），靠加大相对厚度撑住 16px。
 * - 分发不在这里做：生成完执行 node voxio-ui/brand/sync-tokens.mjs，
 *   它按 ASSETS 表把产物拷到四端，并提供 --check 漂移门禁。
 *
 * 依赖：本机 Chrome（无头光栅化）。ICO 用 PNG 载荷（Vista+ 与所有现代浏览器都支持），
 * 因此不需要 PowerShell/System.Drawing，也能装下 256px 这种 BMP 装不下的尺寸。
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const brand = path.dirname(fileURLToPath(import.meta.url));
const pngDir = path.join(brand, 'png');
const icoDir = path.join(brand, 'ico');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'voxio-brand-'));
const CHROME = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const only = (process.argv.find((a) => a.startsWith('--only=')) || '').slice(7);

/** 标记位图尺寸：16/32 给 favicon 与托盘，64/128 给侧栏与窗口，256/512 给登录页与文档 */
const MARK_SIZES = [16, 32, 64, 128, 256, 512];
/** 浏览器标签页图标 */
const FAVICON_SIZES = [16, 32, 48];
/** Windows 应用图标（任务栏 / 托盘 / 快捷方式 / exe） */
const APP_ICON_SIZES = [16, 32, 48, 64, 128, 256];

/** 光栅化任务：源 SVG -> 输出名（方形尺寸） */
const SQUARE = [
  ...MARK_SIZES.map((s) => ({ svg: 'logo-primary.svg', name: `mark-${s}`, size: s })),
  ...[64, 128, 256].map((s) => ({ svg: 'logo-primary-white.svg', name: `mark-white-${s}`, size: s })),
  ...[16, 32, 48, 64, 128, 256, 512].map((s) => ({ svg: 'favicon.svg', name: `favicon-${s}`, size: s })),
  ...[16, 32, 48, 64, 128, 256, 512].map((s) => ({ svg: 'app-icon.svg', name: `app-icon-${s}`, size: s }))
];
/** 非方形任务：组合标志与分享图 */
const RECT = [
  { svg: 'logo-horizontal.svg', name: 'horizontal', w: 640, h: 160 },
  { svg: 'logo-horizontal.svg', name: 'horizontal-2x', w: 1280, h: 320 },
  { svg: 'logo-horizontal-white.svg', name: 'horizontal-white', w: 640, h: 160 },
  { svg: 'logo-vertical.svg', name: 'vertical', w: 320, h: 320 },
  { svg: 'logo-vertical.svg', name: 'vertical-2x', w: 640, h: 640 },
  { svg: 'logo-vertical-white.svg', name: 'vertical-white', w: 320, h: 320 },
  { svg: 'og-image.svg', name: 'og-image', w: 1200, h: 630 }
];

for (const d of [pngDir, icoDir]) fs.mkdirSync(d, { recursive: true });

function toWin(p) {
  return execFileSync('cygpath', ['-w', p], { encoding: 'utf8' }).trim();
}

/** 强制 SVG 的 width/height 后再渲染：Chrome 只按元素尺寸出图，viewBox 不变所以比例恒定 */
function rasterize(svgFile, outPng, width, height) {
  const source = fs.readFileSync(path.join(brand, svgFile), 'utf8')
    .replace(/<svg\b([^>]*?)>/, (m, attrs) => `<svg${attrs.replace(/\s(?:width|height)="[^"]*"/g, '')} width="${width}" height="${height}">`);
  const html = path.join(tmp, `render-${path.basename(outPng, '.png')}.html`);
  fs.writeFileSync(html, '<!doctype html><meta charset="utf-8">'
    + '<style>html,body{margin:0;padding:0;background:transparent}svg{display:block}</style>' + source);
  if (fs.existsSync(outPng)) fs.rmSync(outPng);
  execFileSync(CHROME, [
    '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
    '--default-background-color=00000000', `--window-size=${width},${height}`,
    `--screenshot=${toWin(outPng)}`, `file:///${toWin(html).replace(/\\/g, '/')}`
  ], { stdio: 'ignore' });
  if (!fs.existsSync(outPng)) throw new Error(`Chrome 未产出 ${outPng}`);
  const buf = fs.readFileSync(outPng);
  // 光栅化结果必须是 PNG（89 50 4E 47），否则后面的 ICO 载荷长度字段全是垃圾
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error(`${outPng} 不是 PNG`);
  const [w, h] = [buf.readUInt32BE(16), buf.readUInt32BE(20)];
  if (w !== width || h !== height) throw new Error(`${outPng} 尺寸 ${w}x${h}，期望 ${width}x${height}`);
  console.log(`  PNG ${path.basename(outPng)} ${width}x${height} (${(buf.length / 1024).toFixed(1)} KB)`);
}

/** PNG 载荷直接拼多尺寸 ICO：目录项里的宽高 0 表示 256 */
function buildIco(entries, outIco) {
  const header = Buffer.alloc(6 + entries.length * 16);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(entries.length, 4);
  let offset = header.length;
  entries.forEach((e, i) => {
    const p = 6 + i * 16;
    header.writeUInt8(e.size >= 256 ? 0 : e.size, p);
    header.writeUInt8(e.size >= 256 ? 0 : e.size, p + 1);
    header.writeUInt8(0, p + 2);
    header.writeUInt8(0, p + 3);
    header.writeUInt16LE(1, p + 4);
    header.writeUInt16LE(32, p + 6);
    header.writeUInt32LE(e.payload.length, p + 8);
    header.writeUInt32LE(offset, p + 12);
    offset += e.payload.length;
  });
  fs.writeFileSync(outIco, Buffer.concat([header, ...entries.map((e) => e.payload)]));
  const total = fs.statSync(outIco).size;
  console.log(`  ICO ${path.basename(outIco)} 含 ${entries.map((e) => e.size).join('/')}px（${(total / 1024).toFixed(1)} KB）`);
}

const pngOf = (name) => path.join(pngDir, `${name}.png`);

console.log('[brand] 光栅化方形产物');
if (!only || only === 'png' || only === 'mark') {
  for (const job of SQUARE) rasterize(job.svg, pngOf(job.name), job.size, job.size);
}

console.log('[brand] 光栅化组合标志与分享图');
if (!only || only === 'png' || only === 'logo') {
  for (const job of RECT) rasterize(job.svg, pngOf(job.name), job.w, job.h);
}

console.log('[brand] 打包 ICO');
if (!only || only === 'ico') {
  buildIco(FAVICON_SIZES.map((s) => ({ size: s, payload: fs.readFileSync(pngOf(`favicon-${s}`)) })),
    path.join(icoDir, 'favicon.ico'));
  buildIco(APP_ICON_SIZES.map((s) => ({ size: s, payload: fs.readFileSync(pngOf(`app-icon-${s}`)) })),
    path.join(icoDir, 'app-icon.ico'));
}

/** 只保留本轮生成的 PNG：改名或删掉某个尺寸后，旧位图不能留在仓库里被误引用 */
const keep = new Set([...SQUARE, ...RECT].map((j) => `${j.name}.png`));
for (const f of fs.readdirSync(pngDir)) {
  if (!keep.has(f) && !only) {
    fs.rmSync(path.join(pngDir, f));
    console.log(`  清理旧位图 ${f}`);
  }
}

fs.rmSync(tmp, { recursive: true, force: true });
console.log('[brand] 完成。接着执行 node voxio-ui/brand/sync-tokens.mjs 分发到四端');
