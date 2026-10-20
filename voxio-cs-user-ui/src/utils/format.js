/**
 * 三端统一时间口径（阶段12 功能点20）：今天 HH:mm / 昨天 HH:mm / 更早 YYYY-MM-DD HH:mm。
 * 与管理后台 utils/time.js 的 timeText() 是同一条规则，改就两处一起改。
 */
export function formatTime(ts) {
  if (!ts) return ''
  const date = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  const hhmm = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  const day = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  const today = new Date()
  if (day(date) === day(today)) return hhmm
  if (day(date) === day(new Date(today.getTime() - 86400000))) return `昨天 ${hhmm}`
  return `${day(date)} ${hhmm}`
}
