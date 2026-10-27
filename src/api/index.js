import { MockAcademicApi } from './mock'

// M1 阶段固定使用 Mock；M2 接入真实接口后由 VITE_DATA_MODE=real 切换
export const DATA_MODE = import.meta.env.VITE_DATA_MODE || 'mock'
export const api = new MockAcademicApi()
