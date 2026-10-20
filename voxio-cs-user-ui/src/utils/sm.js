/**
 * 国密统一加密层（前端）—— 归属必须写清，别把它读成「管理后台已加密」
 *
 * 运行时调用方只有访客网页端（voxio-cs-user-ui）：聊天会话密钥协商、帧 HMAC 与 SM4-GCM 加解密。
 * 管理后台（voxio-ui）这份是**零调用点的同源副本**：其构建产物 dist 里搜不到任何 SM 实现（被 tree-shake 掉），
 * 后台页面看到的明文一律由服务端解密后下发，密文只存在于数据库与网关线路。
 * 那为什么留一份：三端一份契约要求两端逐字节相同，二次开发时可以直接复用同一套实现与固定向量。
 * 所以这份在管理后台里**仅供契约同源比对与固定向量核对**，不承担任何后台页面的运行时加密职责。
 *
 * 编码契约与 Java 的 com.voxio.common.utils.crypto.SmCodec 逐条对齐，三端互通靠固定向量收敛：
 *   base64    标准表带 = 填充；十六进制只用于密钥材料与固定向量
 *   SM4 密钥  16 字节，hex 小写 32 位
 *   SM4-CBC   PKCS7 填充，packed = base64( iv(16) || ciphertext )
 *   SM4-GCM   12 字节 IV、16 字节标签，packed = base64( iv(12) || ciphertext || tag(16) )
 *   SM3       hex 小写 64 位；HMAC-SM3 同样 hex 小写
 *   HKDF      SM3-HKDF(ikm, salt, info) 按 RFC5869，ikm/salt 为 hex，info 为 UTF-8，输出 hex
 *   SM2       私钥 hex64、公钥 hex130（04||X||Y）；密文 C1C3C2 hex；签名 SM3withSM2 DER hex
 *   落库前缀  sm4-gcm${keyVersion}$base64(...)，版本号直接是十进制整数、不带 v（如 sm4-gcm$1$；0 表示会话未协商密钥时的主密钥兜底子钥）
 *
 * sm-crypto 只提供 SM3/HMAC-SM3、SM2、SM4 的 ECB/CBC，GCM 与 HKDF 在这里按标准补齐，
 * 底层块加密仍然只用 sm-crypto，不引第二个加密库。
 */
import smCrypto from 'sm-crypto'

const { sm2, sm3, sm4 } = smCrypto

export const SM2_USER_ID = '1234567812345678'
export const SM4_KEY_BYTES = 16
export const GCM_IV_BYTES = 12
export const GCM_TAG_BYTES = 16
export const SM2_CIPHER_MODE = 1 // 1 = C1C3C2，与 Java 侧一致

/* ------------------------------------------------------------------ 编码 */

export function utf8ToBytes(text) {
  return new TextEncoder().encode(text)
}

export function bytesToUtf8(bytes) {
  return new TextDecoder().decode(bytes)
}

export function bytesToHex(bytes) {
  let out = ''
  for (const b of bytes) out += b.toString(16).padStart(2, '0')
  return out
}

export function hexToBytes(hex) {
  const out = new Uint8Array(hex.length / 2)
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16)
  return out
}

export function bytesToBase64(bytes) {
  let binary = ''
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i])
  return btoa(binary)
}

export function base64ToBytes(text) {
  const binary = atob(text)
  const out = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i)
  return out
}

export function concatBytes(...parts) {
  const total = parts.reduce((n, p) => n + p.length, 0)
  const out = new Uint8Array(total)
  let at = 0
  for (const p of parts) { out.set(p, at); at += p.length }
  return out
}

export function randomBytes(length) {
  const out = new Uint8Array(length)
  globalThis.crypto.getRandomValues(out)
  return out
}

/** 会话密钥：16 字节随机，hex 传递 */
export function randomKeyHex() {
  return bytesToHex(randomBytes(SM4_KEY_BYTES))
}

/* ------------------------------------------------------------------ SM3 */

export function sm3Hex(data) {
  return sm3(toBytes(data))
}

export function hmacSm3Hex(key, data) {
  return sm3(toBytes(data), { key: toBytes(key) })
}

function toBytes(input) {
  if (typeof input === 'string') {
    // 约定：十六进制串按原始字节解释前先由调用方显式转换，这里只按 UTF-8 处理文本
    return utf8ToBytes(input)
  }
  return input
}

/** 定长比较，避免标签校验被计时探测 */
export function constantTimeEquals(a, b) {
  const x = toBytes(a)
  const y = toBytes(b)
  if (x.length !== y.length) return false
  let diff = 0
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i]
  return diff === 0
}

/* ------------------------------------------------------------------ HKDF-SM3 */

/** RFC5869：salt/info 为 UTF-8 文本，ikmHex 为十六进制输入材料，返回 length 字节 hex */
export function hkdfSm3Hex(ikmHex, saltHex, info, length) {
  const ikm = hexToBytes(ikmHex)
  const salt = saltHex ? hexToBytes(saltHex) : new Uint8Array(32)
  const infoBytes = utf8ToBytes(info || '')
  const prk = hexToBytes(hmacSm3Hex(salt, ikm))
  let okm = new Uint8Array(0)
  let previous = new Uint8Array(0)
  for (let counter = 1; okm.length < length; counter++) {
    const input = concatBytes(previous, infoBytes, Uint8Array.from([counter]))
    previous = hexToBytes(hmacSm3Hex(prk, input))
    okm = concatBytes(okm, previous)
  }
  return bytesToHex(okm.subarray(0, length))
}

/* ------------------------------------------------------------------ SM4 分组原语 */

function sm4BlockEncrypt(block, keyHex) {
  return Uint8Array.from(sm4.encrypt(Array.from(block), keyHex, { mode: 'ecb', padding: 'none', output: 'array' }))
}

function xorInto(target, source, offset = 0) {
  for (let i = 0; i < 16; i++) target[i] ^= source[offset + i] ?? 0
}

/** GF(2^128) 乘法，GCM 的右移实现（多项式 x^128+x^7+x^2+x+1 → 0xe1） */
function gmul(x, y) {
  const z = new Uint8Array(16)
  const v = Uint8Array.from(y)
  for (let bit = 0; bit < 128; bit++) {
    if (((x[bit >> 3] >>> (7 - (bit & 7))) & 1) === 1) xorInto(z, v)
    const lsb = v[15] & 1
    for (let i = 15; i > 0; i--) v[i] = ((v[i] >>> 1) | (v[i - 1] << 7)) & 0xff
    v[0] >>>= 1
    if (lsb) v[0] ^= 0xe1
  }
  return z
}

function ghash(h, aad, ciphertext) {
  let y = new Uint8Array(16)
  const absorb = (block) => { xorInto(y, block); y = gmul(y, h) }
  for (let off = 0; off < aad.length; off += 16) {
    const block = new Uint8Array(16)
    block.set(aad.subarray(off, Math.min(off + 16, aad.length)))
    absorb(block)
  }
  for (let off = 0; off < ciphertext.length; off += 16) {
    const block = new Uint8Array(16)
    block.set(ciphertext.subarray(off, Math.min(off + 16, ciphertext.length)))
    absorb(block)
  }
  const lens = new Uint8Array(16)
  writeUint64Bits(lens, 0, aad.length * 8)
  writeUint64Bits(lens, 8, ciphertext.length * 8)
  absorb(lens)
  return y
}

function writeUint64Bits(target, offset, value) {
  const high = Math.floor(value / 0x100000000)
  const low = value >>> 0
  for (let i = 0; i < 4; i++) target[offset + i] = (high >>> (24 - i * 8)) & 0xff
  for (let i = 0; i < 4; i++) target[offset + 4 + i] = (low >>> (24 - i * 8)) & 0xff
}

function increase32(counter) {
  for (let i = 15; i >= 12; i--) {
    counter[i] = (counter[i] + 1) & 0xff
    if (counter[i] !== 0) break
  }
}

function gctr(keyHex, initialCounter, input) {
  const out = new Uint8Array(input.length)
  const counter = Uint8Array.from(initialCounter)
  for (let off = 0; off < input.length; off += 16) {
    const stream = sm4BlockEncrypt(counter, keyHex)
    const take = Math.min(16, input.length - off)
    for (let i = 0; i < take; i++) out[off + i] = input[off + i] ^ stream[i]
    increase32(counter)
  }
  return out
}

/* ------------------------------------------------------------------ SM4-CBC / GCM */

/** packed = base64(iv || ciphertext)，IV 随机并随密文传递 */
export function sm4CbcEncryptPacked(keyHex, data) {
  const iv = randomBytes(16)
  const cipherHex = sm4.encrypt(Array.from(toBytes(data)), keyHex, { mode: 'cbc', iv: bytesToHex(iv) })
  return bytesToBase64(concatBytes(iv, hexToBytes(cipherHex)))
}

export function sm4CbcDecryptPacked(keyHex, packedBase64) {
  const packed = base64ToBytes(packedBase64)
  const iv = bytesToHex(packed.subarray(0, 16))
  const cipherHex = bytesToHex(packed.subarray(16))
  // output:'array' 才能拿到原始字节：默认输出会按 UTF-8 解码，非文本明文会被毁掉
  return Uint8Array.from(sm4.decrypt(cipherHex, keyHex, { mode: 'cbc', iv, output: 'array' }))
}

/**
 * SM4-GCM 加密：返回 base64(iv || ciphertext || tag)
 * ivHex 只给固定向量用，业务一律随机 IV
 */
export function sm4GcmEncrypt(keyHex, data, ivHex) {
  const key = normalizeKeyHex(keyHex)
  const iv = ivHex ? hexToBytes(ivHex) : randomBytes(GCM_IV_BYTES)
  if (iv.length !== GCM_IV_BYTES) throw new Error('SM4-GCM 的 IV 必须是 12 字节')
  const plain = toBytes(data)
  const h = sm4BlockEncrypt(new Uint8Array(16), key)
  const j0 = concatBytes(iv, new Uint8Array([0, 0, 0, 1]))
  const ciphertext = gctr(key, increaseCopy(j0), plain)
  const tag = gctr(key, j0, ghash(h, new Uint8Array(0), ciphertext))
  return bytesToBase64(concatBytes(iv, ciphertext, tag))
}

export function sm4GcmDecrypt(keyHex, packedBase64) {
  const key = normalizeKeyHex(keyHex)
  const packed = base64ToBytes(packedBase64)
  if (packed.length < GCM_IV_BYTES + GCM_TAG_BYTES) throw new Error('SM4-GCM 密文长度不足')
  const iv = packed.subarray(0, GCM_IV_BYTES)
  const ciphertext = packed.subarray(GCM_IV_BYTES, packed.length - GCM_TAG_BYTES)
  const tag = packed.subarray(packed.length - GCM_TAG_BYTES)
  const h = sm4BlockEncrypt(new Uint8Array(16), key)
  const j0 = concatBytes(iv, new Uint8Array([0, 0, 0, 1]))
  const expected = gctr(key, j0, ghash(h, new Uint8Array(0), ciphertext))
  if (!constantTimeEquals(expected, tag)) throw new Error('SM4-GCM 校验失败：密文被篡改或密钥不符')
  return gctr(key, increaseCopy(j0), ciphertext)
}

/** GCM 解密后的文本（消息正文都是 UTF-8） */
export function sm4GcmDecryptText(keyHex, packedBase64) {
  return bytesToUtf8(sm4GcmDecrypt(keyHex, packedBase64))
}

function increaseCopy(counter) {
  const copy = Uint8Array.from(counter)
  increase32(copy)
  return copy
}

function normalizeKeyHex(keyHex) {
  if (typeof keyHex !== 'string' || keyHex.length !== SM4_KEY_BYTES * 2) {
    throw new Error('SM4 密钥必须是 32 位十六进制串')
  }
  return keyHex
}

/* ------------------------------------------------------------------ SM2 */

/** 密文 hex 不带 04 前缀时 sm-crypto 会自动补，Java(BouncyCastle) 需要 04||C1，这里统一补齐 */
export function sm2Encrypt(publicKeyHex, data) {
  const cipherHex = sm2.doEncrypt(toBytesText(data), publicKeyHex, SM2_CIPHER_MODE)
  return cipherHex.startsWith('04') ? cipherHex : '04' + cipherHex
}

export function sm2Decrypt(privateKeyHex, cipherHex) {
  const body = cipherHex.startsWith('04') ? cipherHex.slice(2) : cipherHex
  return sm2.doDecrypt(body, privateKeyHex, SM2_CIPHER_MODE, { output: 'array' })
}

export function sm2DecryptText(privateKeyHex, cipherHex) {
  return bytesToUtf8(Uint8Array.from(sm2Decrypt(privateKeyHex, cipherHex)))
}

function toBytesText(data) {
  if (typeof data === 'string') return data
  return bytesToUtf8(data)
}

export function sm2Sign(privateKeyHex, data, publicKeyHex) {
  return sm2.doSignature(toBytesText(data), privateKeyHex, {
    hash: true,
    der: true,
    publicKey: publicKeyHex,
    userId: SM2_USER_ID
  })
}

export function sm2Verify(publicKeyHex, data, signatureHex) {
  try {
    return sm2.doVerifySignature(toBytesText(data), signatureHex, publicKeyHex, {
      hash: true,
      der: true,
      userId: SM2_USER_ID
    })
  } catch {
    return false
  }
}

export function sm2PublicKeyFromPrivate(privateKeyHex) {
  return sm2.getPublicKeyFromPrivateKey(privateKeyHex)
}

/** 临时 SM2 密钥对：密钥协商时用临时公钥接收服务端回传的会话密钥 */
export function sm2GenerateKeyPair() {
  const pair = sm2.generateKeyPairHex()
  return { privateKey: pair.privateKey, publicKey: pair.publicKey }
}

/* ------------------------------------------------------------------ 帧完整性 */

// 与 Java ChatFrameMac 的常量逐字一致
export const CONTROL_SALT_TEXT = 'voxio-desk-control-v1'
export const CONTROL_INFO = 'kf-control-hmac'

export const CONTROL_FRAME_SEPARATOR = String.fromCharCode(10)

/** 连接级 HMAC 密钥：由鉴权令牌派生，鉴权成功瞬间两端各自算出，无需额外协商 */
export function controlKeyHex(token) {
  const ikmHex = sm3(utf8ToBytes(token))
  return hkdfSm3Hex(ikmHex, bytesToHex(utf8ToBytes(CONTROL_SALT_TEXT)), CONTROL_INFO, 16)
}

/**
 * 规范化帧（不含 hmac 自身）：九个头字段用换行符连接，最后接 canon(body)
 *
 * canon(body) 为键升序的 k=v 用 & 连接；值只做 百分号/与号/等号/换行 四条转义；null 值两端都跳过。
 * 与 Java ChatFrameMac、C# ChatFrameMac 的实现必须逐字符一致，由 accept-phase10 的向量断言守住。
 */
export function canonicalFrame(frame) {
  const head = [frame.cmd, frame.seq, frame.sid, frame.ts, frame.type, frame.mid,
    frame.encrypt, frame.algo, frame.keyVersion].map(nzOrEmpty).join(CONTROL_FRAME_SEPARATOR)
  return head + CONTROL_FRAME_SEPARATOR + canonicalBody(frame.body)
}

export function signFrame(keyHex, frame) {
  // 密钥按 16 字节的原始材料参与 HMAC，与 Java/C# 的 unhex(keyHex) 一致（不是把 hex 串当文本）
  return hmacSm3Hex(hexToBytes(keyHex), utf8ToBytes(canonicalFrame(frame)))
}

export function verifyFrame(keyHex, frame, hmac) {
  return !!keyHex && !!hmac && constantTimeEquals(signFrame(keyHex, frame), String(hmac).toLowerCase())
}

function nzOrEmpty(raw) {
  return raw === null || raw === undefined ? '' : String(raw)
}

function canonicalBody(body) {
  if (!body || typeof body !== 'object') return ''
  return Object.keys(body)
    .filter(k => body[k] !== null && body[k] !== undefined)
    .sort()
    .map(k => escapeField(k) + '=' + escapeField(scalar(body[k])))
    .join('&')
}

function scalar(raw) {
  if (typeof raw === 'boolean') return raw ? 'true' : 'false'
  return String(raw)
}

function escapeField(text) {
  return String(text).replace(/%/g, '%25').replace(/&/g, '%26').replace(/=/g, '%3D')
    .split(CONTROL_FRAME_SEPARATOR).join('%0A')
}

/* ------------------------------------------------------------------ 落库前缀 */

/**
 * 密文列前缀契约（与 Java `SmCodec.STORE_PREFIX` 同形）：`sm4-gcm${keyVersion}$base64(...)`，
 * 版本号直接是十进制整数、不带 `v` 前缀。库里真实形态是 `sm4-gcm$0$…` / `sm4-gcm$1$…`。
 */
export function wrapCipher(packedBase64, keyVersion, algo = 'sm4-gcm') {
  return `${algo}$${keyVersion}$${packedBase64}`
}

export function unwrapCipher(text) {
  if (typeof text !== 'string') return { encrypted: false, value: text }
  const parts = text.split('$')
  if (parts.length !== 3 || !parts[0].startsWith('sm4-')) return { encrypted: false, value: text }
  return { encrypted: true, algo: parts[0], keyVersion: Number(parts[1]), payload: parts[2] }
}

/* ------------------------------------------------------------------ 固定向量 */

// 与 com.voxio.common.utils.crypto.SmTestVectors 的常量逐字一致
export const VECTORS = {
  message: 'Voxio 国密互通向量😀',
  sm4KeyHex: '0123456789abcdeffedcba9876543210',
  cbcIvHex: '000102030405060708090a0b0c0d0e0f',
  gcmIvHex: '000102030405060708090a0b',
  hmacKeyHex: '31323334353637383132333435363738',
  hkdfIkmHex: '0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b',
  hkdfSaltHex: '000102030405060708090a0b0c',
  hkdfInfo: 'voxio-desk-chat',
  hkdfLength: 32,
  sm2PrivateHex: '3945209f7b21c43b3f1479c3954447d3d02e7e4e60a2c98688b37f203d86963c',
  controlToken: 'voxio-demo-token-0123456789',
  frameTs: 1700000000000,
  frameNote: '含中文&等号=与%号'
}

/** 帧完整性向量：与 Java SmTestVectors / C# SmTestVectors 的同名键逐字符比对 */
export function buildFrameVectors() {
  const frame = {
    cmd: 'PING',
    seq: 7,
    ts: VECTORS.frameTs,
    body: { serverTime: VECTORS.frameTs, typing: 1, enabled: true, note: VECTORS.frameNote, skipped: null }
  }
  const key = controlKeyHex(VECTORS.controlToken)
  return {
    controlToken: VECTORS.controlToken,
    controlKeyHex: key,
    frameCanonical: canonicalFrame(frame),
    frameHmacHex: signFrame(key, frame)
  }
}

/** 产出与 Java SmTestVectors.build() 同名键的结果，供三端逐字段比对 */
export function buildVectors() {
  const plain = utf8ToBytes(VECTORS.message)
  return {
    message: VECTORS.message,
    sm3MessageHex: sm3(plain),
    sm3HmacHex: sm3(plain, { key: hexToBytes(VECTORS.hmacKeyHex) }),
    hkdfHex: hkdfSm3Hex(VECTORS.hkdfIkmHex, VECTORS.hkdfSaltHex, VECTORS.hkdfInfo, VECTORS.hkdfLength),
    sm4CbcPackedBase64: sm4CbcEncryptPackedWithIv(VECTORS.sm4KeyHex, VECTORS.cbcIvHex, plain),
    sm4GcmPackedBase64: sm4GcmEncrypt(VECTORS.sm4KeyHex, plain, VECTORS.gcmIvHex),
    sm2PublicKeyHex: sm2PublicKeyFromPrivate(VECTORS.sm2PrivateHex),
    sm2SignatureHex: sm2Sign(VECTORS.sm2PrivateHex, plain, sm2PublicKeyFromPrivate(VECTORS.sm2PrivateHex)),
    ...buildFrameVectors()
  }
}

function sm4CbcEncryptPackedWithIv(keyHex, ivHex, data) {
  const iv = hexToBytes(ivHex)
  const cipherHex = sm4.encrypt(Array.from(data), keyHex, { mode: 'cbc', iv: ivHex })
  return bytesToBase64(concatBytes(iv, hexToBytes(cipherHex)))
}

export default {
  SM2_USER_ID,
  utf8ToBytes,
  bytesToUtf8,
  hexToBytes,
  bytesToHex,
  base64ToBytes,
  bytesToBase64,
  randomKeyHex,
  sm3Hex,
  hmacSm3Hex,
  hkdfSm3Hex,
  sm4CbcEncryptPacked,
  sm4CbcDecryptPacked,
  sm4GcmEncrypt,
  sm4GcmDecrypt,
  sm4GcmDecryptText,
  sm2Encrypt,
  sm2Decrypt,
  sm2DecryptText,
  sm2Sign,
  sm2Verify,
  sm2GenerateKeyPair,
  controlKeyHex,
  canonicalFrame,
  signFrame,
  verifyFrame,
  wrapCipher,
  unwrapCipher,
  buildVectors,
  buildFrameVectors,
  VECTORS
}
