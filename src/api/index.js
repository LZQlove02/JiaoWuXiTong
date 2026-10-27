import { MockAcademicApi } from './mock'

// 个人教务数据固定使用 Mock（真实登录因 HTTP 明文传输被阻断）
// 公开内容（通知/办事指南/校历）走 api/public.js
export const DATA_MODE = import.meta.env.VITE_DATA_MODE || 'mock'
export const api = new MockAcademicApi()
