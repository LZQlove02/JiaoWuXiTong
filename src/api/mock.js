import {
  mockUser,
  mockTerms,
  mockCourses,
  mockSessions,
  mockGrades,
  mockExams
} from '../mock/data'

const delay = (ms = 200) => new Promise((resolve) => setTimeout(resolve, ms))

function summarizeGrades(grades) {
  const totalCredits = grades.reduce((sum, item) => sum + item.credit, 0)
  const weighted = grades.reduce((sum, item) => sum + item.credit * item.gpa, 0)
  return {
    totalCredits,
    gpa: totalCredits ? Number((weighted / totalCredits).toFixed(2)) : 0,
    count: grades.length,
    failed: grades.filter((item) => Number(item.score) < 60).length
  }
}

export class MockAcademicApi {
  async login(username, password) {
    await delay(450)
    if (!username.trim() || !password.trim()) {
      throw new Error('请输入学号和密码')
    }
    return { ...mockUser, id: username.trim() }
  }

  async logout() {
    await delay(100)
  }

  async getProfile() {
    await delay()
    return { ...mockUser }
  }

  async getTerms() {
    await delay(100)
    return mockTerms.map((item) => ({ ...item }))
  }

  async getSchedule(term) {
    await delay()
    return {
      term,
      courses: mockCourses.map((item) => ({ ...item })),
      sessions: mockSessions.map((item) => ({ ...item }))
    }
  }

  async getGrades(term) {
    await delay()
    return {
      term,
      grades: mockGrades.map((item) => ({ ...item })),
      summary: summarizeGrades(mockGrades)
    }
  }

  async getExams(term) {
    await delay()
    return mockExams.map((item) => ({ ...item }))
  }

  async getCourse(id) {
    await delay(120)
    const course = mockCourses.find((item) => item.id === id)
    if (!course) return null
    return {
      ...course,
      sessions: mockSessions.filter((item) => item.courseId === id)
    }
  }
}
