/**
 * 客服口令强度规则（阶段10.4）
 *
 * 与后端 com.voxio.kf.service.KfPasswordPolicy 同口径：不少于 8 位，
 * 且「大写字母 / 小写字母 / 数字 / 符号」四类字符中至少覆盖 3 类。
 * 阈值在后端 kf_security_config（password.policy.min-length / min-categories）里可调，
 * 这里写的是缺省值——服务端永远是唯一裁判，前端只是让客服在提交前就知道该怎么改。
 */

const MIN_LENGTH = 8
const MIN_CATEGORIES = 3
const CATEGORY_NAMES = ['大写字母', '小写字母', '数字', '符号']

/** 命中的字符类别数：非字母数字一律算符号 */
export function matchedCategories(value) {
  let upper = false, lower = false, digit = false, symbol = false
  for (const ch of value || '') {
    if (ch >= 'A' && ch <= 'Z') upper = true
    else if (ch >= 'a' && ch <= 'z') lower = true
    else if (ch >= '0' && ch <= '9') digit = true
    else symbol = true
  }
  return (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0)
}

/** 返回错误文案，合规时返回空串 */
export function kfPasswordError(value) {
  if (!value) {
    return '密码不能为空'
  }
  if (value.length < MIN_LENGTH) {
    return `密码不能少于 ${MIN_LENGTH} 位`
  }
  const hit = matchedCategories(value)
  if (hit < MIN_CATEGORIES) {
    return `密码需包含${CATEGORY_NAMES.join('、')}中的至少 ${MIN_CATEGORIES} 类（当前只含 ${hit} 类）`
  }
  return ''
}

export function useKfPasswordRule() {
  return [
    {
      required: true,
      trigger: 'blur',
      validator: (rule, value, callback) => {
        const error = kfPasswordError(value)
        error ? callback(new Error(error)) : callback()
      }
    }
  ]
}

/** 表单下方常驻的口径说明，避免客服照着报错一条条试 */
export const KF_PASSWORD_HINT = `不少于 ${MIN_LENGTH} 位，且含大写字母、小写字母、数字、符号中的至少 ${MIN_CATEGORIES} 类`
