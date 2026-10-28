import { ZfAcademicApi } from './zf'

// 个人教务数据统一来自学校统一认证后的教务系统；
// 公开内容（通知/办事指南/校历）走 api/public.js。
const createTarget = () => new ZfAcademicApi()
let target = createTarget()

export function setAcademicApi(next) {
  target = next || createTarget()
}

export function resetAcademicApi() {
  target = createTarget()
}

export const api = {
  getTerms: (...args) => target.getTerms(...args),
  getSchedule: (...args) => target.getSchedule(...args),
  getGrades: (...args) => target.getGrades(...args),
  getExams: (...args) => target.getExams(...args),
  getEvaluations: (...args) => target.getEvaluations(...args),
  getCourseSelectionStatus: (...args) => target.getCourseSelectionStatus(...args),
  getCourse: (...args) => target.getCourse(...args)
}
