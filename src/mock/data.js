export const mockUser = {
  id: '20256030154',
  name: '吕祖庆',
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

export const mockNotices = [
  { id: 'n1', title: '关于 2026-2027 学年第一学期期中教学检查的通知', date: '2026-10-26', source: '教务处', summary: '本学期期中教学检查于第 10-11 周进行，请各二级学院按要求做好课堂听课与学生座谈安排。' },
  { id: 'n2', title: '2026 年下半年大学英语四六级考试报名通知', date: '2026-10-24', source: '教务处', summary: '报名时间 10 月 27 日至 11 月 3 日，请登录全国大学英语四六级考试报名网完成缴费。' },
  { id: 'n3', title: '关于开展 2026-2027 学年第一学期学生评教工作的通知', date: '2026-10-22', source: '教学质量监控中心', summary: '评教系统于第 12 周开放，请全体在校学生在规定时间内完成网上评教。' },
  { id: 'n4', title: '2026-2027 学年第一学期期末考试安排（预排）', date: '2026-10-20', source: '教务处', summary: '期末考试将于第 17-18 周进行，具体时间与考场安排以正式通知为准。' },
  { id: 'n5', title: '关于做好 2026 年秋季学期重修报名工作的通知', date: '2026-10-18', source: '教务处', summary: '重修报名时间为 10 月 20 日至 10 月 31 日，逾期不再受理。' },
  { id: 'n6', title: '2026 年国家奖学金、国家励志奖学金评审结果公示', date: '2026-10-15', source: '学生工作处', summary: '现将 2026 年国家奖学金、国家励志奖学金拟推荐名单予以公示，公示期 5 个工作日。' },
  { id: 'n7', title: '关于调整国庆节后教学安排的通知', date: '2026-10-08', source: '教务处', summary: '10 月 11 日（周六）补 10 月 7 日（周三）的课程，请师生注意课表变化。' },
  { id: 'n8', title: '2026-2027 学年第一学期校历', date: '2026-09-01', source: '教务处', summary: '本学期共 20 周，第 1 周 8 月 31 日开学，第 17-18 周期末考试，第 19-20 周实践教学。' },
  { id: 'n9', title: '关于 2025 级学生转专业申请审核结果的公示', date: '2026-09-20', source: '教务处', summary: '经学生申请、二级学院审核、学校审批，现将 2025 级转专业审核结果予以公示。' },
  { id: 'n10', title: '关于加强课堂考勤管理的通知', date: '2026-09-10', source: '教务处', summary: '各任课教师须严格执行课堂考勤制度，缺勤累计达三分之一者取消考试资格。' }
]

export const mockEvaluations = [
  { id: 'v1', courseName: '高等数学(A)', teacher: '张明', status: 'pending', deadline: '2026-12-05' },
  { id: 'v2', courseName: '大学英语(一)', teacher: '李静', status: 'pending', deadline: '2026-12-05' },
  { id: 'v3', courseName: '程序设计基础', teacher: '王强', status: 'pending', deadline: '2026-12-05' },
  { id: 'v4', courseName: '体育(一)', teacher: '刘伟', status: 'done', deadline: '2026-12-05' }
]

export const mockCalendar = [
  { date: '2026-08-31', title: '学生报到、开学第一周' },
  { date: '2026-09-26', title: '校运动会' },
  { date: '2026-10-01', title: '国庆节放假（10.1-10.7）' },
  { date: '2026-10-11', title: '补 10 月 7 日（周三）课程' },
  { date: '2026-11-05', title: '期末考试周开始（第 17 周）' },
  { date: '2026-12-25', title: '寒假开始' }
]

export const mockGuides = [
  { id: 'gd1', title: '重修工作流程', source: '教务处' },
  { id: 'gd2', title: '学生实习管理工作流程', source: '教务处' },
  { id: 'gd3', title: '随班跟读、免修免考、重修费免交、缓考申请流程', source: '教务处' },
  { id: 'gd4', title: '结业转毕业办理说明', source: '教务处' },
  { id: 'gd5', title: '休学、复学、保留学籍办理流程', source: '教务处' },
  { id: 'gd6', title: '转专业申请流程', source: '教务处' }
]
