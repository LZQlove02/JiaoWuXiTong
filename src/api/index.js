import { ZfAcademicApi } from './zf'
import { notifySessionExpired } from '../utils/sessionExpiry'

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

// 所有业务请求都经过这里：任一处判定会话失效，统一通知登录态清理，
// 不再依赖各个页面/仓库自行处理，避免用户卡在「所有页面都报会话失效」。
function guarded(method) {
  return async (...args) => {
    try {
      return await target[method](...args)
    } catch (error) {
      if (error?.sessionExpired) notifySessionExpired()
      throw error
    }
  }
}

export const api = {
  getTerms: guarded('getTerms'),
  getSchedule: guarded('getSchedule'),
  getGrades: guarded('getGrades'),
  getAllGrades: guarded('getAllGrades'),
  getExams: guarded('getExams'),
  getEvaluations: guarded('getEvaluations'),
  getCourseSelectionStatus: guarded('getCourseSelectionStatus'),
  getPortalNotices: guarded('getPortalNotices'),
  getPortalNoticeList: guarded('getPortalNoticeList'),
  getPortalNoticeDetail: guarded('getPortalNoticeDetail'),
  getMessages: guarded('getMessages'),
  getCourse: guarded('getCourse')
}
