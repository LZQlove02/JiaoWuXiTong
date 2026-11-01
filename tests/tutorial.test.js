import { describe, expect, it } from 'vitest'
import { icons } from '../src/utils/icons'
import { tutorialFaqs, tutorialSections } from '../src/utils/tutorial'

function allNonEmpty(list) {
  return list.every((line) => typeof line === 'string' && line.trim())
}

describe('使用教程内容', () => {
  it('章节字段完整且文案非空', () => {
    expect(tutorialSections.length).toBeGreaterThanOrEqual(6)
    tutorialSections.forEach((section) => {
      expect(section.id).toMatch(/^[a-z-]+$/)
      expect(section.title.trim()).toBeTruthy()
      expect(section.summary.trim()).toBeTruthy()
      expect(section.items.length).toBeGreaterThan(0)
      expect(allNonEmpty(section.items)).toBe(true)
    })
  })

  it('章节与 FAQ 的 id 唯一', () => {
    const ids = [...tutorialSections, ...tutorialFaqs].map((item) => item.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('章节图标都存在于 AppIcon', () => {
    tutorialSections.forEach((section) => {
      expect(Object.keys(icons)).toContain(section.icon)
    })
  })

  it('FAQ 包含问题与至少一条解答', () => {
    expect(tutorialFaqs.length).toBeGreaterThanOrEqual(5)
    tutorialFaqs.forEach((faq) => {
      expect(faq.question.trim()).toBeTruthy()
      expect(faq.items.length).toBeGreaterThan(0)
      expect(allNonEmpty(faq.items)).toBe(true)
    })
  })
})
