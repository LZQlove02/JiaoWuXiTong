export const mockUser = {
  id: '20250010001',
  name: '张同学',
  className: '2025 级软件技术 1 班',
  major: '软件技术',
  college: '电子信息工程学院',
  avatar: ''
}

export const mockTerms = [
  {
    xnm: '2026',
    xqm: '3',
    label: '2026-2027 学年第一学期',
    startDate: '2026-08-31',
    isCurrent: true
  },
  {
    xnm: '2025',
    xqm: '12',
    label: '2025-2026 学年第二学期',
    startDate: '2026-02-23',
    isCurrent: false
  }
]

export const mockCourses = [
  {
    id: 'c1',
    code: 'MATH1001',
    name: '高等数学(A)',
    teacher: '张明',
    credits: 5,
    type: '公共基础课',
    color: '#1B5FAA'
  },
  {
    id: 'c2',
    code: 'ENGL1001',
    name: '大学英语(一)',
    teacher: '李静',
    credits: 4,
    type: '公共基础课',
    color: '#2E8B57'
  },
  {
    id: 'c3',
    code: 'CS1001',
    name: '程序设计基础',
    teacher: '王强',
    credits: 4,
    type: '专业基础课',
    color: '#B7791F'
  },
  {
    id: 'c4',
    code: 'PE1001',
    name: '体育(一)',
    teacher: '刘伟',
    credits: 2,
    type: '公共基础课',
    color: '#8E44AD'
  },
  {
    id: 'c5',
    code: 'POL1001',
    name: '思想道德与法治',
    teacher: '陈芳',
    credits: 3,
    type: '公共基础课',
    color: '#C0392B'
  },
  {
    id: 'c6',
    code: 'CS1002',
    name: '计算机导论',
    teacher: '赵磊',
    credits: 2,
    type: '专业基础课',
    color: '#0E7490'
  },
  {
    id: 'c7',
    code: 'MATH1002',
    name: '线性代数',
    teacher: '孙丽',
    credits: 3,
    type: '公共基础课',
    color: '#4B5563'
  },
  {
    id: 'c8',
    code: 'PSY1001',
    name: '大学生心理健康',
    teacher: '周敏',
    credits: 2,
    type: '公共基础课',
    color: '#DB2777'
  }
]

export const mockSessions = [
  { courseId: 'c1', weekday: 1, startSection: 1, endSection: 2, weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16], room: '博学楼 A201' },
  { courseId: 'c1', weekday: 3, startSection: 3, endSection: 4, weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16], room: '博学楼 A201' },
  { courseId: 'c2', weekday: 1, startSection: 3, endSection: 4, weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16], room: '明理楼 B103' },
  { courseId: 'c2', weekday: 4, startSection: 1, endSection: 2, weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16], room: '明理楼 B103' },
  { courseId: 'c3', weekday: 2, startSection: 1, endSection: 2, weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16], room: '实训楼 C302' },
  { courseId: 'c3', weekday: 5, startSection: 3, endSection: 4, weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16], room: '实训楼 C302' },
  { courseId: 'c4', weekday: 2, startSection: 5, endSection: 6, weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16], room: '田径场' },
  { courseId: 'c5', weekday: 3, startSection: 1, endSection: 2, weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16], room: '博学楼 A105' },
  { courseId: 'c6', weekday: 4, startSection: 3, endSection: 4, weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16], room: '实训楼 C201' },
  { courseId: 'c7', weekday: 5, startSection: 1, endSection: 2, weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16], room: '博学楼 A203' },
  { courseId: 'c8', weekday: 3, startSection: 7, endSection: 8, weeks: [1, 3, 5, 7, 9, 11, 13, 15], room: '明理楼 B201' }
]

export const mockGrades = [
  { courseId: 'g1', courseName: '高等数学(B)', credit: 6, score: 88, gpa: 3.8, examType: '考试', term: '2026-2027-1' },
  { courseId: 'g2', courseName: '大学英语(一)', credit: 4, score: 92, gpa: 4.2, examType: '考试', term: '2026-2027-1' },
  { courseId: 'g3', courseName: '程序设计基础', credit: 4, score: 95, gpa: 4.5, examType: '考试', term: '2026-2027-1' },
  { courseId: 'g4', courseName: '体育(一)', credit: 2, score: 85, gpa: 3.5, examType: '考查', term: '2026-2027-1' },
  { courseId: 'g5', courseName: '思想道德与法治', credit: 3, score: 90, gpa: 4.0, examType: '考试', term: '2026-2027-1' },
  { courseId: 'g6', courseName: '计算机导论', credit: 2, score: 91, gpa: 4.1, examType: '考查', term: '2026-2027-1' },
  { courseId: 'g7', courseName: '线性代数', credit: 3, score: 78, gpa: 2.8, examType: '考试', term: '2026-2027-1' },
  { courseId: 'g8', courseName: '大学物理', credit: 3, score: 82, gpa: 3.2, examType: '考试', term: '2026-2027-1' },
  { courseId: 'g9', courseName: '军事理论', credit: 2, score: 87, gpa: 3.7, examType: '考查', term: '2026-2027-1' },
  { courseId: 'g10', courseName: '劳动教育', credit: 1, score: 90, gpa: 4.0, examType: '考查', term: '2026-2027-1' },
  { courseId: 'g11', courseName: '创新创业基础', credit: 2, score: 89, gpa: 3.9, examType: '考查', term: '2026-2027-1' },
  { courseId: 'g12', courseName: '大学生心理健康', credit: 2, score: 86, gpa: 3.6, examType: '考查', term: '2026-2027-1' }
]

export const mockExams = [
  { id: 'e1', courseName: '高等数学(A)', date: '2026-11-05', startTime: '09:00', endTime: '11:00', room: '博学楼 A201', seat: '12', type: '期末考试' },
  { id: 'e2', courseName: '大学英语(一)', date: '2026-11-07', startTime: '14:30', endTime: '16:30', room: '明理楼 B103', seat: '25', type: '期末考试' },
  { id: 'e3', courseName: '程序设计基础', date: '2026-11-10', startTime: '09:00', endTime: '11:00', room: '实训楼 C302', seat: '08', type: '期末考试' },
  { id: 'e4', courseName: '线性代数', date: '2026-11-12', startTime: '14:30', endTime: '16:30', room: '博学楼 A203', seat: '17', type: '期末考试' },
  { id: 'e5', courseName: '思想道德与法治', date: '2026-11-15', startTime: '09:00', endTime: '11:00', room: '博学楼 A105', seat: '21', type: '期末考试' }
]
