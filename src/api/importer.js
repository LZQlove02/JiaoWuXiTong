const WEEKDAY_MAP = {
  一: 1,
  二: 2,
  三: 3,
  四: 4,
  五: 5,
  六: 6,
  日: 7,
  天: 7
}

const COURSE_COLORS = ['#1B5FAA', '#2F855A', '#B7791F', '#805AD5', '#C05621', '#2C7A7B']

function normalizeText(value) {
  return (value || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim()
}

function cellLines(cell) {
  const html = cell.innerHTML.replace(/<br\s*\/?>/gi, '\n')
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html')
  return (doc.body.textContent || '')
    .split('\n')
    .map(normalizeText)
    .filter(Boolean)
}

export function parseWeeks(text) {
  if (!text) return Array.from({ length: 16 }, (_, index) => index + 1)
  const weeks = new Set()
  for (const match of text.matchAll(/(\d+)\s*[-~至]\s*(\d+)\s*周?/g)) {
    const start = Number(match[1])
    const end = Number(match[2])
    for (let week = start; week <= end; week += 1) weeks.add(week)
  }
  for (const match of text.matchAll(/(\d+)\s*周/g)) {
    weeks.add(Number(match[1]))
  }
  let result = [...weeks].sort((a, b) => a - b)
  if (!result.length) result = Array.from({ length: 16 }, (_, index) => index + 1)
  if (/单/.test(text) && !/双/.test(text)) result = result.filter((week) => week % 2 === 1)
  if (/双/.test(text) && !/单/.test(text)) result = result.filter((week) => week % 2 === 0)
  return result
}

function parseSectionRange(text) {
  const range = text.match(/(\d+)\s*[-~至]\s*(\d+)/)
  if (range) {
    return { start: Number(range[1]), end: Number(range[2]) }
  }
  const single = text.match(/(\d+)\s*节/)
  if (single) return { start: Number(single[1]), end: Number(single[1]) }
  return null
}

function parseCourseCell(lines) {
  if (!lines.length) return null
  const weekLine = lines.find((line) => /周/.test(line))
  const roomLine = lines.find((line) => /楼|室|教室|机房|实训|体育馆|田径场|场馆/.test(line))
  const name = lines.find(
    (line) => line !== weekLine && line !== roomLine && !/周/.test(line)
  )
  if (!name) return null
  const teacher =
    lines.find((line) => line !== name && line !== weekLine && line !== roomLine) || ''
  return {
    name,
    teacher,
    room: roomLine || '',
    weeks: parseWeeks(weekLine)
  }
}

export function parseScheduleHtml(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const table = [...doc.querySelectorAll('table')].find(
    (item) => /星期[一二三四五六日天]/.test(item.textContent) && /节/.test(item.textContent)
  )
  if (!table) return null

  const rows = [...table.querySelectorAll('tr')]
  const headerRow = rows.find((row) => /星期[一二三四五六日天]/.test(row.textContent))
  if (!headerRow) return null

  const weekdayColumns = {}
  ;[...headerRow.children].forEach((cell, index) => {
    const match = normalizeText(cell.textContent).match(/星期([一二三四五六日天])/)
    if (match) weekdayColumns[index] = WEEKDAY_MAP[match[1]]
  })
  if (!Object.keys(weekdayColumns).length) return null

  const courses = new Map()
  const sessions = []

  for (const row of rows) {
    if (row === headerRow) continue
    const cells = [...row.children]
    if (!cells.length) continue
    const range = parseSectionRange(normalizeText(cells[0].textContent))
    if (!range) continue

    for (const [indexText, weekday] of Object.entries(weekdayColumns)) {
      const cell = cells[Number(indexText)]
      if (!cell) continue
      const blocks = [...cell.querySelectorAll('.kbcontent, .kbcontent1, [class*="kbcontent"]')]
      const groups = blocks.length ? blocks.map(cellLines) : [cellLines(cell)]

      for (const lines of groups) {
        const parsed = parseCourseCell(lines)
        if (!parsed) continue
        if (!courses.has(parsed.name)) {
          courses.set(parsed.name, {
            id: `import-${courses.size + 1}`,
            code: '',
            name: parsed.name,
            teacher: parsed.teacher,
            credits: 0,
            type: '导入课程',
            color: COURSE_COLORS[courses.size % COURSE_COLORS.length]
          })
        }
        sessions.push({
          courseId: courses.get(parsed.name).id,
          weekday,
          startSection: range.start,
          endSection: range.end,
          weeks: parsed.weeks,
          room: parsed.room
        })
      }
    }
  }

  if (!sessions.length) return null
  return { courses: [...courses.values()], sessions }
}

function summarizeGrades(grades) {
  const totalCredits = grades.reduce((sum, item) => sum + (Number(item.credit) || 0), 0)
  const weighted = grades.reduce(
    (sum, item) => sum + (Number(item.credit) || 0) * (Number(item.gpa) || 0),
    0
  )
  return {
    totalCredits,
    gpa: totalCredits ? Number((weighted / totalCredits).toFixed(2)) : 0,
    count: grades.length,
    failed: grades.filter((item) => Number(item.score) < 60).length
  }
}

function findColumn(headers, names) {
  return headers.findIndex((header) => names.some((name) => header.includes(name)))
}

export function parseGradesHtml(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html')

  for (const table of doc.querySelectorAll('table')) {
    const rows = [...table.querySelectorAll('tr')]
    if (rows.length < 2) continue
    const headers = [...rows[0].children].map((cell) => normalizeText(cell.textContent))
    const nameIndex = findColumn(headers, ['课程名称', '课程名', '课程'])
    const scoreIndex = findColumn(headers, ['成绩', '分数'])
    const creditIndex = findColumn(headers, ['学分'])
    if (nameIndex < 0 || scoreIndex < 0 || creditIndex < 0) continue

    const codeIndex = findColumn(headers, ['课程代码', '课程号'])
    const gpaIndex = findColumn(headers, ['绩点'])
    const typeIndex = findColumn(headers, ['考试性质', '考核方式', '课程性质'])

    const grades = []
    for (const row of rows.slice(1)) {
      const cells = [...row.children].map((cell) => normalizeText(cell.textContent))
      if (!cells[nameIndex] || !cells[scoreIndex]) continue
      const scoreText = cells[scoreIndex]
      const score = /^\d+(\.\d+)?$/.test(scoreText) ? Number(scoreText) : scoreText
      grades.push({
        courseId: codeIndex >= 0 ? cells[codeIndex] : `import-${grades.length + 1}`,
        courseName: cells[nameIndex],
        credit: creditIndex >= 0 ? Number(cells[creditIndex]) || 0 : 0,
        score,
        gpa: gpaIndex >= 0 ? Number(cells[gpaIndex]) || 0 : 0,
        examType: typeIndex >= 0 ? cells[typeIndex] : '',
        term: ''
      })
    }
    if (grades.length) return { grades, summary: summarizeGrades(grades) }
  }
  return null
}

export function parseExamsHtml(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html')

  for (const table of doc.querySelectorAll('table')) {
    const rows = [...table.querySelectorAll('tr')]
    if (rows.length < 2) continue
    const headers = [...rows[0].children].map((cell) => normalizeText(cell.textContent))
    const nameIndex = findColumn(headers, ['考试名称', '课程名称', '课程'])
    const dateIndex = findColumn(headers, ['考试时间', '时间', '日期'])
    const roomIndex = findColumn(headers, ['考试地点', '地点', '教室'])
    const seatIndex = findColumn(headers, ['座位', '座号'])
    if (nameIndex < 0 || dateIndex < 0) continue

    const exams = []
    for (const row of rows.slice(1)) {
      const cells = [...row.children].map((cell) => normalizeText(cell.textContent))
      if (!cells[nameIndex]) continue
      const timeText = cells[dateIndex] || ''
      const dateMatch = timeText.match(/(\d{4}-\d{2}-\d{2})/)
      const timeMatch = timeText.match(/(\d{1,2}:\d{2})\s*[-~至]\s*(\d{1,2}:\d{2})/)
      exams.push({
        id: `import-exam-${exams.length + 1}`,
        courseName: cells[nameIndex],
        date: dateMatch ? dateMatch[1] : timeText,
        startTime: timeMatch ? timeMatch[1] : '',
        endTime: timeMatch ? timeMatch[2] : '',
        room: roomIndex >= 0 ? cells[roomIndex] : '',
        seat: seatIndex >= 0 ? cells[seatIndex] : '',
        type: '考试'
      })
    }
    if (exams.length) return exams
  }
  return null
}
