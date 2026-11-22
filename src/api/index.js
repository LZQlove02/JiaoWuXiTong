import { ZfAcademicApi } from './zf'
import { notifySessionExpired } from '../utils/sessionExpiry'
import { isSessionBlocked } from '../utils/sessionState'

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
    // 票据已确认失效、界面处于只读模式：不再带着无效 Cookie 打网络，
    // 否则每个页面都会各自报一次「会话失效」，也会白白触发静默换票
    if (isSessionBlocked()) throw new Error('登录已过期，请重新登录')
    try {
      return await target[method](...args)
    } catch (error) {
      if (error?.sessionExpired) {
        // 静默重登成功时重放一次原请求，页面不会看到「已失效」的中间态；
        // 重放仍失败则按普通业务错误抛出，不再二次触发静默。
        const recovered = await notifySessionExpired()
        if (recovered) return target[method](...args)
      }
      throw error
    }
  }
}

export const api = {
  getTerms: guarded('getTerms'),
  getSchedule: guarded('getSchedule'),
  getGrades: guarded('getGrades'),
  getAllGrades: guarded('getAllGrades'),
  getEvaluations: guarded('getEvaluations'),
  getCourseSelectionStatus: guarded('getCourseSelectionStatus'),
  getPortalNotices: guarded('getPortalNotices'),
  getPortalNoticeList: guarded('getPortalNoticeList'),
  getPortalNoticeDetail: guarded('getPortalNoticeDetail'),
  getMessages: guarded('getMessages'),
  getCourse: guarded('getCourse')
}
