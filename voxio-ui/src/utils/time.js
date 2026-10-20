/**
 * 三端统一时间口径（阶段12 功能点20）。
 *
 * 列表列用 timeText：今天 HH:mm / 昨天 HH:mm / 更早 YYYY-MM-DD HH:mm；
 * 详情、时间线、审计类字段用 dateTimeText：一律完整到秒，因为那些位置要的是「哪一刻」而不是「大概什么时候」。
 *
 * 同一规则的另一份实现在 voxio-cs-user-ui/src/utils/format.js 的 formatTime()。
 * 两处必须一起改，改一处就是让客服和访客在同一条消息上看到两个时间。
 */

const pad = (n) => String(n).padStart(2, '0')

const dayKey = (y, m, d) => y * 10000 + m * 100 + d

/**
 * 后端给的是不带时区的墙钟串（yyyy-MM-dd HH:mm:ss），这里按字面拆分，不交给 new Date 猜时区；
 * 否则同一条记录在 UTC+8 与 UTC 的浏览器上会差出小时数，历史数据也会整体漂移。
 */
function partsOf(value) {
  if (!value) {
    return null
  }
  if (value instanceof Date) {
    return {
      y: value.getFullYear(),
      mo: value.getMonth() + 1,
      d: value.getDate(),
      hh: pad(value.getHours()),
      mi: pad(value.getMinutes()),
      ss: pad(value.getSeconds())
    }
  }
  const matched = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(String(value))
  if (matched) {
    return {
      y: +matched[1],
      mo: +matched[2],
      d: +matched[3],
      hh: matched[4],
      mi: matched[5],
      ss: matched[6] || '00'
    }
  }
  return null
}

/** 列表用相对口径：一眼能分辨「刚刚 / 昨天 / 哪天」，不必在每一行都读一遍年份 */
export function timeText(value) {
  const p = partsOf(value)
  if (!p) {
    return '-'
  }
  const hhmm = `${p.hh}:${p.mi}`
  const now = new Date()
  const key = dayKey(p.y, p.mo, p.d)
  if (key === dayKey(now.getFullYear(), now.getMonth() + 1, now.getDate())) {
    return hhmm
  }
  const yesterday = new Date(now.getTime() - 86400000)
  if (key === dayKey(yesterday.getFullYear(), yesterday.getMonth() + 1, yesterday.getDate())) {
    return `昨天 ${hhmm}`
  }
  return `${p.y}-${pad(p.mo)}-${pad(p.d)} ${hhmm}`
}

/** 详情用完整时间：精确到秒，供对账、排障与审计引用 */
export function dateTimeText(value) {
  const p = partsOf(value)
  if (!p) {
    return '-'
  }
  return `${p.y}-${pad(p.mo)}-${pad(p.d)} ${p.hh}:${p.mi}:${p.ss}`
}
