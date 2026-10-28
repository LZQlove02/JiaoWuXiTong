// 学期参数的统一推算，供统一认证数据层使用
export function termParamsFor(date = new Date()) {
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  if (month >= 8) return { xnm: String(year), xqm: '3' }
  if (month === 1) return { xnm: String(year - 1), xqm: '3' }
  return { xnm: String(year - 1), xqm: '12' }
}

const TERM_NAMES = { 3: '第一学期', 12: '第二学期', 16: '第三学期（暑期）' }

export function buildTerm(xnm, xqm, options = {}) {
  const year = String(xnm)
  const quarter = String(xqm)
  return {
    xnm: year,
    xqm: quarter,
    label: options.label
      || `${year}-${Number(year) + 1} 学年${TERM_NAMES[quarter] || `第${quarter}学期`}`,
    startDate: quarter === '3' ? `${year}-08-31` : `${Number(year) + 1}-02-23`,
    isCurrent: Boolean(options.isCurrent)
  }
}

