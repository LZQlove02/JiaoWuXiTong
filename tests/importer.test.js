import { describe, expect, it } from 'vitest'
import { parseExamsHtml, parseGradesHtml, parseScheduleHtml } from '../src/api/importer'

describe('导入网页解析', () => {
  it('解析课表表格', () => {
    const html = `
      <table id="kbtable">
        <tr><th>节次</th><th>星期一</th><th>星期二</th></tr>
        <tr>
          <td>1-2节</td>
          <td><div class="kbcontent">高等数学<br>张老师<br>1-16周<br>教学楼A101</div></td>
          <td></td>
        </tr>
        <tr>
          <td>3-4节</td>
          <td></td>
          <td><div class="kbcontent">大学英语<br>李老师<br>2-16周(双)<br>教学楼B202</div></td>
        </tr>
      </table>
    `
    const result = parseScheduleHtml(html)
    expect(result.courses).toHaveLength(2)
    expect(result.sessions).toHaveLength(2)
    expect(result.sessions[0]).toMatchObject({
      weekday: 1,
      startSection: 1,
      endSection: 2,
      room: '教学楼A101'
    })
    expect(result.sessions[1].weekday).toBe(2)
    expect(result.sessions[1].weeks).toEqual([2, 4, 6, 8, 10, 12, 14, 16])
  })

  it('解析成绩表格与统计', () => {
    const html = `
      <table>
        <tr><th>课程名称</th><th>学分</th><th>成绩</th><th>绩点</th></tr>
        <tr><td>高等数学</td><td>5</td><td>90</td><td>4.0</td></tr>
        <tr><td>大学英语</td><td>3</td><td>55</td><td>0</td></tr>
      </table>
    `
    const result = parseGradesHtml(html)
    expect(result.grades).toHaveLength(2)
    expect(result.summary.totalCredits).toBe(8)
    expect(result.summary.failed).toBe(1)
    expect(result.summary.gpa).toBe(2.5)
  })

  it('解析考试表格', () => {
    const html = `
      <table>
        <tr><th>考试名称</th><th>考试时间</th><th>考试地点</th><th>座位号</th></tr>
        <tr><td>高等数学</td><td>2026-01-05 09:00-11:00</td><td>A101</td><td>12</td></tr>
      </table>
    `
    const result = parseExamsHtml(html)
    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({
      courseName: '高等数学',
      date: '2026-01-05',
      startTime: '09:00',
      endTime: '11:00',
      room: 'A101',
      seat: '12'
    })
  })

  it('无法识别时返回 null', () => {
    expect(parseScheduleHtml('<div>没有表格</div>')).toBeNull()
    expect(parseGradesHtml('<table><tr><th>姓名</th></tr></table>')).toBeNull()
    expect(parseExamsHtml('<div></div>')).toBeNull()
  })
})
