/**
 * 开放接口的浏览器侧短缓存（阶段11 功能点21）
 *
 * 访客每次打开对话框都要拉一遍入口状态（服务时间、在线客服数、是否可留言），这份数据
 * 几十秒内几乎不变，但每一次往返都是一趟公网 HTTP。缓在 sessionStorage 里：
 *   - 用 sessionStorage 而不是 localStorage：访客标签页关掉就该重新取，
 *     避免客服上下线被本地旧值长期盖住；
 *   - TTL 由 vite.config 的 APP_CONFIG.openCacheTtl 下发（改一处重新构建即可），
 *     这里再给一份兜底默认值，配置缺失也不会退化成「永不刷新」；
 *   - 只缓存读接口，任何写操作（提交留言）都不进这里。
 */
const PREFIX = 'voxio_open_cache:'

const DEFAULT_TTL = { status: 30000 }

function ttlOf(name) {
  const configured = import.meta.env.VITE_APP_OPEN_CACHE_TTL || {}
  const value = Number(configured[name] ?? DEFAULT_TTL[name])
  if (!Number.isFinite(value) || value <= 0) {
    // 0 或负数 = 这一档不缓存；上限 10 分钟，避免配置手滑把访客端变成静态快照
    return 0
  }
  return Math.min(value, 600000)
}

function read(key, ttlMs) {
  if (!ttlMs) return null
  try {
    const raw = sessionStorage.getItem(PREFIX + key)
    if (!raw) return null
    const hit = JSON.parse(raw)
    if (!hit || Date.now() - hit.at > ttlMs) {
      sessionStorage.removeItem(PREFIX + key)
      return null
    }
    return hit.value
  } catch {
    return null
  }
}

function write(key, value) {
  try {
    sessionStorage.setItem(PREFIX + key, JSON.stringify({ at: Date.now(), value }))
  } catch {
    /* 隐私模式或配额满：缓存是加速件，拿不到只是回到每次都出网 */
  }
}

/**
 * @param name status，决定用哪档 TTL
 * @param key 同一 name 下的细分键（如入口状态的作用域）
 * @param loader 未命中时的真实请求，返回整份响应
 * @param force 跳过缓存直接出网（点「人工客服」时排队人数必须是最新的）
 */
export async function cachedOpen(name, key, loader, force = false) {
  const cacheKey = name + ':' + key
  if (!force) {
    const hit = read(cacheKey, ttlOf(name))
    if (hit !== null) return hit
  }
  const value = await loader()
  write(cacheKey, value)
  return value
}
