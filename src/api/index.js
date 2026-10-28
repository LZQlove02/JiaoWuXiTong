import { MockAcademicApi } from './mock'
import { HybridAcademicApi, hasAnyImport } from './imported'

// 个人教务数据默认使用 Mock；导入网页数据后优先使用导入结果。
// 统一认证登录成功后，切换到 ZfAcademicApi 的实时数据。
// 公开内容（通知/办事指南/校历）走 api/public.js。
export { hasAnyImport }

const defaultApi = new HybridAcademicApi(new MockAcademicApi())
let target = defaultApi

export function setAcademicApi(next) {
  target = next || defaultApi
}

export function resetAcademicApi() {
  target = defaultApi
}

export const api = {
  login: (...args) => target.login(...args),
  logout: (...args) => target.logout(...args),
  getProfile: (...args) => target.getProfile(...args),
  getTerms: (...args) => target.getTerms(...args),
  getSchedule: (...args) => target.getSchedule(...args),
  getGrades: (...args) => target.getGrades(...args),
  getExams: (...args) => target.getExams(...args),
  getCourse: (...args) => target.getCourse(...args)
}
