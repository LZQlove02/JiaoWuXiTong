import { describe, expect, it } from 'vitest'
import { buildDiagnostics, formatStamp } from '../src/utils/diagnostics'
import {
  BODY_LIMIT,
  FEEDBACK_EMAIL,
  SUBJECT_LIMIT,
  buildFeedbackBody,
  buildFeedbackMailto,
  buildFeedbackSubject
} from '../src/utils/feedback'

describe('diagnostics', () => {
  const device = { manufacturer: 'Xiaomi', model: '2211133C', android: '16', sdk: 36 }

  it('包含版本、设备与系统信息', () => {
    const text = buildDiagnostics({
      version: '1.3.0',
      device,
      theme: 'dark',
      platform: 'Android',
      online: true,
      now: new Date('2026-10-30T21:30:00')
    })
    expect(text).toContain('应用版本：v1.3.0')
    expect(text).toContain('设备：Xiaomi 2211133C')
    expect(text).toContain('系统：Android 16（API 36）')
    expect(text).toContain('主题：深色')
    expect(text).toContain('网络：在线')
    expect(text).toContain('生成时间：2026-10-30 21:30')
  })

  it('缺失字段时降级为未知', () => {
    const text = buildDiagnostics({ version: '', device: null, online: false })
    expect(text).toContain('应用版本：未知')
    expect(text).toContain('设备：未知')
    expect(text).toContain('系统：Android 未知')
    expect(text).toContain('主题：跟随系统')
    expect(text).toContain('网络：离线')
  })

  it('时间戳格式化', () => {
    expect(formatStamp(new Date('2026-01-02T03:04:00'))).toBe('2026-01-02 03:04')
    expect(formatStamp(new Date('bad'))).toBe('')
  })

  it('有本地崩溃摘要时追加到诊断信息', () => {
    const crash = '时间：2026-10-31 23:00\n异常：java.lang.RuntimeException: boom'
    const text = buildDiagnostics({ version: '1.3.5', device, crash })
    expect(text).toContain('【最近崩溃】')
    expect(text).toContain('异常：java.lang.RuntimeException: boom')

    const without = buildDiagnostics({ version: '1.3.5', device, crash: '  ' })
    expect(without).not.toContain('【最近崩溃】')
  })
})

describe('feedback', () => {
  it('主题带版本且不超长', () => {
    expect(buildFeedbackSubject('1.3.0')).toBe('柳职教务使用反馈（v1.3.0）')
    expect(buildFeedbackSubject('')).toBe('柳职教务使用反馈')
    expect(buildFeedbackSubject('x'.repeat(300)).length).toBe(SUBJECT_LIMIT)
  })

  it('正文拼装与截断', () => {
    const body = buildFeedbackBody({
      description: '成绩页空白',
      diagnostics: '应用版本：v1.3.0',
      contact: 'QQ 123'
    })
    expect(body).toContain('【问题描述】\n成绩页空白')
    expect(body).toContain('【诊断信息】\n应用版本：v1.3.0')
    expect(body).toContain('【联系方式】\nQQ 123')

    const empty = buildFeedbackBody({ description: '   ' })
    expect(empty).toContain('（未填写）')
    expect(empty).not.toContain('【联系方式】')

    const long = buildFeedbackBody({ description: 'a'.repeat(BODY_LIMIT + 100) })
    expect(long.length).toBeLessThanOrEqual(BODY_LIMIT)
    expect(long.endsWith('（已截断）')).toBe(true)
  })

  it('邮件链接编码', () => {
    const link = buildFeedbackMailto('柳职教务使用反馈（v1.3.0）', '问题描述\n换行 & 特殊字符')
    expect(link.startsWith(`mailto:${FEEDBACK_EMAIL}?subject=`)).toBe(true)
    expect(link).not.toContain(' ')
    expect(link).not.toContain('\n')
    expect(link).toContain('%26')
  })
})
