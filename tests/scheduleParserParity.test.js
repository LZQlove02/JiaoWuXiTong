import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { mapSchedule } from '../src/api/zf'

// 与 Android 端 ScheduleParserTest 共用同一份 fixture（android/app/build.gradle 把该目录加进测试资源）
const fixture = JSON.parse(
  fs.readFileSync(path.resolve(process.cwd(), 'tests/fixtures/schedule-kbList.json'), 'utf8')
)

const term = {
  xnm: '2026',
  xqm: '3',
  label: '2026-2027 学年第一学期',
  startDate: '2026-08-31',
  isCurrent: true
}

describe('课表解析：JS / Java 共享 fixture', () => {
  it('mapSchedule 与 expectedSessions 完全一致', () => {
    const { sessions } = mapSchedule(fixture, term)
    const expected = fixture.expectedSessions.map(({ courseName, ...rest }) => rest)
    expect(sessions).toEqual(expected)
  })
})