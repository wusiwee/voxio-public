const PREFIX = 'sb.list.'

/**
 * 列表页筛选条件的落地与记忆。
 *
 * 为什么需要它：离线留言里的工单号、工单详情里的关联会话，本来都想把管理员
 * 直接带到「已经筛好」的列表上，但目标页从不读 URL 参数，跳过去等于跳回全量列表；
 * 另外从详情页返回列表时筛选和页码全丢，得重新筛一遍。
 *
 * 约定：URL 参数优先于本地记忆（点了带条件的入口就该看到那一条），
 * 落地顺序因此固定为「先 restoreFilters 再 applyQueryFilters」；
 * 只认目标页 queryParams 里已有的字段，避免页面上凭空多出筛不掉的隐藏条件。
 */

function normalize(value) {
  return value === 'undefined' || value === 'null' ? undefined : value
}

/**
 * 把路由 query 写进 queryParams。cast 声明哪些字段要转数字（下拉选项与后端参数都是数字，
 * 从 URL 拿到的永远是字符串，不转就匹配不上、筛选看起来「没生效」）。
 */
export function applyQueryFilters(queryParams, query = {}, cast = {}) {
  Object.keys(query).forEach(key => {
    if (!(key in queryParams)) {
      return
    }
    const raw = normalize(Array.isArray(query[key]) ? query[key][0] : query[key])
    if (raw === undefined || raw === '') {
      return
    }
    const to = cast[key]
    if (to === Number) {
      const n = Number(raw)
      queryParams[key] = Number.isNaN(n) ? undefined : n
    } else {
      queryParams[key] = raw
    }
  })
}

/** 记住本次筛选（只记页面声明的字段，页码与分页大小不记：回到列表该从第一页看起）。 */
export function rememberFilters(key, queryParams, fields) {
  if (!key) {
    return
  }
  const snapshot = {}
  fields.forEach(field => {
    const value = queryParams[field]
    if (value !== undefined && value !== null && value !== '') {
      snapshot[field] = value
    }
  })
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify(snapshot))
  } catch {
    // 隐私模式或配额满：记住记不住都不该挡住列表
  }
}

/**
 * 恢复上次筛选；URL 已经带了同名字段时不覆盖。
 * force 里的字段连页面默认值一起覆盖——会话监控默认只看「进行中」，
 * 若管理员上次看的是「全部」，不强制覆盖就等于记忆没生效。
 */
export function restoreFilters(key, queryParams, fields, force = []) {
  if (!key) {
    return false
  }
  let snapshot
  try {
    snapshot = JSON.parse(localStorage.getItem(PREFIX + key) || '{}')
  } catch {
    return false
  }
  let applied = false
  fields.forEach(field => {
    const value = normalize(snapshot[field])
    if (value === undefined || !(field in queryParams)) {
      return
    }
    const empty = queryParams[field] === undefined || queryParams[field] === null
    if (empty || force.includes(field)) {
      queryParams[field] = value
      applied = true
    }
  })
  return applied
}

/** 重置查询时同步清掉本地记忆，否则「重置」完一刷新又回到旧筛选。 */
export function forgetFilters(key) {
  if (!key) {
    return
  }
  try {
    localStorage.removeItem(PREFIX + key)
  } catch {
    // 同上
  }
}
