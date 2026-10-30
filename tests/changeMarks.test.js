import { describe, expect, it } from 'vitest'
import { buildChangeMarks, markKey, markOf } from '../src/utils/changeMarks'

function entry(overrides) {
  return {
    id: 'c_test',
    type: 'cancel',
    courseId: 'C1',
    courseName: '高等数学',
    quiet: false,
    read: false,
    ...overrides
  }
}

const slot = (weekday, startSection, endSection = startSection + 1) => ({
  weekday,
  startSection,
  endSection,
  room: 'A101',
  weeksLabel: '1-16周'
})

describe('changeMarks', () => {
  it('调课/新增映射到新格位，停课进入虚框', () => {
    const { marks, ghosts } = buildChangeMarks([
      entry({ id: 'a', type: 'move', from: slot(1, 3), to: slot(3, 5) }),
      entry({ id: 'b', type: 'add', to: slot(5, 7) }),
      entry({ id: 'c', type: 'cancel', from: slot(2, 1) })
    ])
    expect(marks[markKey(slot(3, 5))]).toBe('move')
    expect(marks[markKey(slot(5, 7))]).toBe('add')
    expect(ghosts).toHaveLength(1)
    expect(ghosts[0].id).toBe('c')
  })

  it('同一格同时有调课与新增时，调课优先', () => {
    const { marks } = buildChangeMarks([
      entry({ id: 'a', type: 'add', to: slot(3, 5) }),
      entry({ id: 'b', type: 'move', to: slot(3, 5) })
    ])
    expect(marks[markKey(slot(3, 5))]).toBe('move')
  })

  it('换教室不标记，quiet 的条目全部忽略', () => {
    const { marks, ghosts } = buildChangeMarks([
      entry({ id: 'a', type: 'room', from: slot(1, 3), to: { ...slot(1, 3), room: 'B203' } }),
      entry({ id: 'b', type: 'cancel', from: slot(2, 1), quiet: true })
    ])
    expect(Object.keys(marks)).toHaveLength(0)
    expect(ghosts).toHaveLength(0)
  })

  it('多个停课格子按格位去重', () => {
    const { ghosts } = buildChangeMarks([
      entry({ id: 'a', type: 'cancel', from: slot(2, 1) }),
      entry({ id: 'b', type: 'cancel', from: slot(2, 1) })
    ])
    expect(ghosts).toHaveLength(1)
  })

  it('markOf 按键查标记，未命中返回空串', () => {
    expect(markOf({ [markKey(slot(3, 5))]: 'move' }, slot(3, 5))).toBe('move')
    expect(markOf({}, slot(3, 5))).toBe('')
    expect(markOf(null, slot(3, 5))).toBe('')
  })
})