import { describe, expect, it } from 'vitest'
import { parseArticle, parseArticleList } from '../src/api/public'

const listHtml = `
<html><body>
  <ul class="infoList pageTPList">
    <li class="first">
      <span class="date">2025-12-22</span>
      <a href="/jwysxglc/tzgg/content_101976">关于转专业审核结果的公示</a>
    </li>
    <li>
      <span class="date">2025-03-17</span>
      <a href="/jwysxglc/tzgg/content_96182">关于本科学生转专业审核结果的公示</a>
    </li>
  </ul>
</body></html>
`

const articleHtml = `
<html><body>
  <div class="articleCon">
    <div class="conTxt">
      <h1>重修工作流程</h1>
      <p>第一段说明。</p>
      <p>第二段说明。</p>
    </div>
  </div>
</body></html>
`

const articleTextOnlyHtml = `
<html><body>
  <div class="articleCon">
    <div class="conTxt">没有段落标签的正文内容</div>
  </div>
</body></html>
`

describe('parseArticleList', () => {
  it('解析正常列表的标题、日期与 id', () => {
    const items = parseArticleList(listHtml)
    expect(items).toHaveLength(2)
    expect(items[0]).toEqual({
      id: 'content_101976',
      title: '关于转专业审核结果的公示',
      date: '2025-12-22',
      url: 'https://www.lzpu.edu.cn/jwysxglc/tzgg/content_101976'
    })
  })

  it('非列表页面返回空数组', () => {
    expect(parseArticleList('<html><body><p>统一身份认证</p></body></html>')).toEqual([])
  })

  it('列表结构变化时返回空数组而不是抛错', () => {
    expect(parseArticleList('<html><body><div class="newList"><a href="/a">x</a></div></body></html>')).toEqual([])
  })

  it('相对链接按列表页地址解析为完整 URL', () => {
    const html = `
      <ul class="infoList">
        <li><span class="date">2025-03-17</span><a href="content_96182">关于转专业审核结果的公示</a></li>
      </ul>
    `
    const items = parseArticleList(html, 'https://www.lzpu.edu.cn/jwysxglc/tzgg')
    expect(items[0].id).toBe('content_96182')
    expect(items[0].url).toBe('https://www.lzpu.edu.cn/jwysxglc/content_96182')
  })
})

describe('parseArticle', () => {
  it('解析标题与段落', () => {
    const data = parseArticle(articleHtml, {
      id: 'content_75320',
      section: 'bszn',
      url: 'https://www.lzpu.edu.cn/jwysxglc/bszn/content_75320'
    })
    expect(data.title).toBe('重修工作流程')
    expect(data.paragraphs).toEqual(['第一段说明。', '第二段说明。'])
    expect(data.text).toBe('')
  })

  it('没有 p 标签时回退到 textContent', () => {
    const data = parseArticle(articleTextOnlyHtml, {
      id: 'content_1',
      section: 'bszn',
      url: 'https://www.lzpu.edu.cn/jwysxglc/bszn/content_1'
    })
    expect(data.paragraphs).toEqual([])
    expect(data.text).toBe('没有段落标签的正文内容')
  })

  it('缺少正文容器时返回 null', () => {
    expect(
      parseArticle('<html><body><p>登录</p></body></html>', {
        id: 'content_1',
        section: 'bszn',
        url: 'https://www.lzpu.edu.cn/x'
      })
    ).toBeNull()
  })

  it('解析正文图片与官网附件链接', () => {
    const html = `
      <html><body>
        <div class="articleCon">
          <div class="conTxt">
            <h1>流程</h1>
            <p>正文</p>
            <p><img src="../images/a.png" /></p>
            <p><a href="/files/表格.pdf">下载表格</a></p>
            <p><a href="https://example.com/x.pdf">外部文件</a></p>
          </div>
        </div>
      </body></html>
    `
    const data = parseArticle(html, {
      id: 'content_1',
      section: 'bszn',
      url: 'https://www.lzpu.edu.cn/jwysxglc/bszn/content_1'
    })
    expect(data.images).toHaveLength(1)
    expect(data.images[0].url).toBe('https://www.lzpu.edu.cn/jwysxglc/images/a.png')
    expect(data.attachments).toHaveLength(1)
    expect(data.attachments[0]).toMatchObject({
      name: '下载表格',
      url: 'https://www.lzpu.edu.cn/files/%E8%A1%A8%E6%A0%BC.pdf'
    })
  })
})
