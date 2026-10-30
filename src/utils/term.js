// 学期参数的统一推算，供统一认证数据层使用
export function termParamsFor(date = new Date()) {
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  if (month >= 8) return { xnm: String(year), xqm: '3' }
  if (month === 7) return { xnm: String(year - 1), xqm: '16' }
  if (month === 1) return { xnm: String(year - 1), xqm: '3' }
  return { xnm: String(year - 1), xqm: '12' }
}

// 主学期取不到时依次尝试相邻学期：暑期短学期、第二学期、下一学年第一学期
export function termCandidatesFor(params) {
  const xnm = Number(params?.xnm)
  const xqm = String(params?.xqm || '')
  const list = []
  const add = (year, quarter) => {
    if (!Number.isFinite(year) || !quarter) return
    if (list.some((item) => item.xnm === String(year) && item.xqm === quarter)) return
    list.push({ xnm: String(year), xqm: quarter })
  }

  add(xnm, xqm)
  if (xqm === '3') {
    add(xnm, '12')
    add(xnm - 1, '16')
  } else if (xqm === '12') {
    add(xnm, '16')
    add(xnm + 1, '3')
  } else {
    add(xnm, '12')
    add(xnm + 1, '3')
  }
  return list
}

const TERM_NAMES = { 3: '第一学期', 12: '第二学期', 16: '第三学期（暑期）' }

export function buildTerm(xnm, xqm, options = {}) {
  const year = String(xnm)
  const quarter = String(xqm)
  const startDate =
    quarter === '3'
      ? `${year}-08-31`
      : quarter === '16'
        ? `${Number(year) + 1}-07-01`
        : `${Number(year) + 1}-02-23`
  return {
    xnm: year,
    xqm: quarter,
    label: options.label
      || `${year}-${Number(year) + 1} 学年${TERM_NAMES[quarter] || `第${quarter}学期`}`,
    startDate,
    isCurrent: Boolean(options.isCurrent)
  }
}
