import { expect, test, type APIResponse, type Page, type TestInfo } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

type CareerPlanningOverview = {
  session?: CareerPlanningSession | null
}

type CareerPlanningSession = {
  id: string
  entryMode: 'AI_DISCOVERY' | 'KNOWN_TARGET'
  version?: number
  profile: { objectiveTaxonomyId?: string | null }
  activeGoal?: { id?: string; recommendationId?: string; title?: string } | null
  recommendationSet?: {
    recommendations: Array<{ id: string; title: string }>
  } | null
  canvas?: {
    version: number
    nodes: Array<{ logicalNodeId: string; type: string; status: string; title: string }>
  } | null
}

type CareerExecution = {
  activePlan?: {
    id: string
    tasks: Array<{ id: string; nodeId?: string | null; title: string; status: string }>
  } | null
}

type ApiEnvelope<T> = { data?: T }

type VerificationChallenge = { challengeId: string }
type DevMailbox = { code: string }
type VerificationConfirmation = { verificationToken: string }

async function data<T>(response: APIResponse): Promise<T> {
  const payload = await response.json() as T | ApiEnvelope<T>
  return 'data' in (payload as ApiEnvelope<T>) && (payload as ApiEnvelope<T>).data
    ? (payload as ApiEnvelope<T>).data as T
    : payload as T
}

async function registerFreshSeeker(page: Page, testInfo: TestInfo): Promise<string> {
  const suffix = `${testInfo.project.name}-${Date.now()}-${Math.random().toString(16).slice(2)}`
    .toLowerCase().replace(/[^a-z0-9-]/g, '-')
  const email = `career-e2e-${suffix}@example.com`
  const challengeResponse = await page.request.post('/api/v1/auth/verifications/request', {
    data: { channel: 'EMAIL', destination: email, purpose: 'REGISTER' },
  })
  expect(challengeResponse.ok(), `注册验证码申请失败：${challengeResponse.status()}`).toBeTruthy()
  const challenge = await data<VerificationChallenge>(challengeResponse)

  const mailboxResponse = await page.request.get(`/internal/dev/mailbox/${encodeURIComponent(email)}`)
  expect(mailboxResponse.ok(), `开发邮箱读取失败：${mailboxResponse.status()}`).toBeTruthy()
  const mailbox = await data<DevMailbox>(mailboxResponse)
  const confirmationResponse = await page.request.post('/api/v1/auth/verifications/confirm', {
    data: { challengeId: challenge.challengeId, destination: email, code: mailbox.code },
  })
  expect(confirmationResponse.ok(), `注册验证码确认失败：${confirmationResponse.status()}`).toBeTruthy()
  const confirmation = await data<VerificationConfirmation>(confirmationResponse)

  const registerResponse = await page.request.post('/api/v1/auth/register', {
    data: {
      channel: 'EMAIL', destination: email, verificationToken: confirmation.verificationToken,
      password: 'CareerE2e123!', acceptedTerms: true, acceptedPrivacy: true,
    },
  })
  expect(registerResponse.ok(), `求职者注册失败：${registerResponse.status()}`).toBeTruthy()
  return email
}

async function chooseSelect(page: Page, label: string, option: string): Promise<void> {
  await page.getByRole('combobox', { name: label, exact: true }).click()
  await page.getByRole('option', { name: option, exact: true }).click()
}

async function deferWhatsNew(page: Page): Promise<void> {
  const remindLater = page.getByRole('button', { name: '稍后查看', exact: true })
  const visible = await remindLater.waitFor({ state: 'visible', timeout: 3_000 })
    .then(() => true)
    .catch(() => false)
  if (!visible) return
  await remindLater.click()
  await expect(remindLater).toBeHidden()
}

async function loginAndOpenActiveSession(page: Page): Promise<CareerPlanningOverview> {
  const login = await page.request.post('/api/v1/auth/login', {
    data: { email: 'seeker', password: 'seeker123' },
  })
  expect(login.ok(), `求职者登录失败：${login.status()}`).toBeTruthy()

  const overviewResponse = await page.request.get('/api/v1/career-planning')
  expect(overviewResponse.ok(), `职业规划概览读取失败：${overviewResponse.status()}`).toBeTruthy()
  const payload = await overviewResponse.json() as CareerPlanningOverview | ApiEnvelope<CareerPlanningOverview>
  const overview = 'data' in payload && payload.data ? payload.data : payload
  expect(overview.session?.id, '本地求职者账号需要至少一条职业规划验收会话').toBeTruthy()

  await page.goto(`/career-planning/${encodeURIComponent(overview.session!.id)}`)
  await expect(page.locator('.workbench-actions button').first()).toBeVisible()
  return overview
}

async function assertNoHorizontalOverflow(page: Page): Promise<void> {
  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth)
}

async function capture(page: Page, testInfo: TestInfo, state: string): Promise<void> {
  const directory = resolve(process.cwd(), '..', '.codex_tmp', 'career-planning-acceptance-current', testInfo.project.name)
  await mkdir(directory, { recursive: true })
  // Capture the settled UI, not the translucent first frame of drawers and modals.
  await page.waitForTimeout(360)
  const body = await page.screenshot({
    fullPage: false,
    path: resolve(directory, `${state}.png`),
  })
  await testInfo.attach(`${state}-${testInfo.project.name}`, {
    body,
    contentType: 'image/png',
  })
}

async function fetchSession(page: Page, sessionId: string): Promise<CareerPlanningSession> {
  const response = await page.request.get(`/api/v1/career-planning/sessions/${encodeURIComponent(sessionId)}`)
  expect(response.ok(), `职业规划会话读取失败：${response.status()}`).toBeTruthy()
  return data<CareerPlanningSession>(response)
}

async function createDirectCareerGoal(page: Page, testInfo: TestInfo): Promise<string> {
  await registerFreshSeeker(page, testInfo)
  await page.goto('/career-planning/new')
  await deferWhatsNew(page)
  await page.getByRole('button', { name: '开始职业方向判定' }).click()
  await chooseSelect(page, '当前身份', '应届生')
  await chooseSelect(page, '最高学历', '本科')
  await chooseSelect(page, '工作年限', '暂无正式经验')
  await chooseSelect(page, '每周学习时间', '10~15小时')
  await page.getByPlaceholder('例如：软件工程').fill('计算机科学与技术')
  await page.getByPlaceholder('例如：2026').fill('2026')

  await page.getByRole('tab', { name: '2 技能与经历' }).click()
  await page.getByPlaceholder('只写真实发生的经历，建议包含你的职责、行动和结果。').fill(
    '在三人课程项目中使用 Java、Spring Boot 和 MySQL 开发订单接口；我负责参数校验、数据表设计、JUnit 测试和接口文档，没有虚构上线数据。',
  )
  await page.getByRole('tab', { name: '3 个人倾向与限制' }).click()
  await page.getByPlaceholder('例如：上海、杭州').fill('杭州')
  await page.getByPlaceholder('例如：希望从机械方向转向软件开发，同时保留行业经验').fill('希望从事 Java 后端开发。')
  await page.getByPlaceholder('例如：未来 3 个月完成方向验证，并形成可展示的项目证据').fill(
    '三个月内补齐后端工程能力，并形成代码、测试报告和复盘记录。',
  )
  await expect(page.getByRole('status')).toContainText('草稿已保存', { timeout: 10_000 })

  await page.getByRole('button', { name: '选择资料授权' }).click()
  const evidenceDialog = page.getByRole('dialog', { name: '选择本次规划可使用的资料' })
  await evidenceDialog.getByRole('button', { name: '确认授权 0 项' }).click()
  await page.getByRole('button', { name: '直接确认画像' }).click()
  await expect(page.getByRole('heading', { name: '确认职业画像' })).toBeVisible()
  await page.getByRole('button', { name: '确认画像并进入推荐' }).click()
  await page.getByRole('button', { name: '生成职业方向 · 1 次' }).click()

  const recommendations = page.locator('.cp-recommendation-list .cp-recommendation')
  await expect.poll(() => recommendations.count(), { timeout: 120_000 }).toBeGreaterThanOrEqual(3)
  await recommendations.first().getByRole('button', { name: '设为目标' }).click()
  const goalDialog = page.getByRole('dialog', { name: '确认目标职业' })
  await goalDialog.getByRole('checkbox').check()
  await goalDialog.getByRole('button', { name: '确认目标并创建画布' }).click()
  await expect(page.locator('.career-canvas-shell')).toBeVisible({ timeout: 30_000 })
  return new URL(page.url()).pathname.split('/').pop()!
}

test('职业方向入口在四个目标视口保持稳定且无安全边界展示', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-1440', '四个指定视口在单一 Chromium 会话中统一验收')
  test.setTimeout(90_000)
  const runtimeErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') runtimeErrors.push(`console: ${message.text()}`)
  })
  page.on('pageerror', error => runtimeErrors.push(`page: ${error.message}`))

  await registerFreshSeeker(page, testInfo)
  const viewports = [
    { width: 1680, height: 940 },
    { width: 1440, height: 900 },
    { width: 1015, height: 898 },
    { width: 390, height: 844 },
  ]

  for (const viewport of viewports) {
    await page.setViewportSize(viewport)
    await page.goto('/career-planning/new')
    await deferWhatsNew(page)

    await expect(page.getByRole('heading', { name: '先确定职业方向，再生成你的能力树' })).toBeVisible()
    await expect(page.locator('.cp-entry')).toHaveCount(2)
    await expect(page.locator('.cp-discovery-steps li')).toHaveCount(3)
    await expect(page.locator('.cp-outcome-list > div')).toHaveCount(4)
    await expect(page.getByText('安全边界', { exact: true })).toHaveCount(0)
    await expect(page.getByRole('checkbox', { name: /允许本次规划调用 AI/ })).toBeChecked()
    await assertNoHorizontalOverflow(page)
    await capture(page, testInfo, `career-entry-${viewport.width}x${viewport.height}-initial`)

    const knownCard = page.locator('.cp-entry--known')
    const before = await knownCard.boundingBox()
    expect(before).not.toBeNull()
    await page.getByRole('button', { name: '填写目标职业' }).click()
    await expect(page.locator('.cp-target-setup')).toBeVisible()
    await page.waitForTimeout(280)
    const after = await knownCard.boundingBox()
    expect(after).not.toBeNull()
    expect(Math.abs(after!.x - before!.x)).toBeLessThanOrEqual(1)
    expect(Math.abs(after!.width - before!.width)).toBeLessThanOrEqual(1)
    expect(Math.abs(after!.height - before!.height)).toBeLessThanOrEqual(2)
    await assertNoHorizontalOverflow(page)

    const narrowText = await page.locator('h1,h2,p,strong,small,button').evaluateAll(elements => elements
      .filter((element) => {
        const text = element.textContent?.trim() ?? ''
        const rect = element.getBoundingClientRect()
        return text.length >= 4 && rect.height > 0 && rect.width > 0 && rect.width < 24
      })
      .map(element => element.textContent?.trim() ?? ''))
    expect(narrowText).toEqual([])
    await capture(page, testInfo, `career-entry-${viewport.width}x${viewport.height}-target-open`)
  }

  expect(runtimeErrors).toEqual([])
})

test('职业规划核心工作台与多画布总览在目标视口可读取', async ({ page }, testInfo) => {
  const runtimeErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') runtimeErrors.push(`console: ${message.text()}`)
  })
  page.on('pageerror', error => runtimeErrors.push(`page: ${error.message}`))
  page.on('response', response => {
    if (response.url().includes('/api/') && response.status() >= 500) {
      runtimeErrors.push(`api: ${response.status()} ${response.url()}`)
    }
  })

  const overview = await loginAndOpenActiveSession(page)
  const workspace = page.locator('main.cp-page')
  const mobile = testInfo.project.name.startsWith('mobile-')
  const topbar = page.locator('.topbar--career')
  const workbenchHeader = page.locator('.workbench-shell-header')
  const [topbarBox, workbenchHeaderBox] = await Promise.all([topbar.boundingBox(), workbenchHeader.boundingBox()])
  expect(topbarBox).not.toBeNull()
  expect(workbenchHeaderBox).not.toBeNull()
  const headerGap = workbenchHeaderBox!.y - (topbarBox!.y + topbarBox!.height)
  if (mobile) expect(Math.abs(headerGap)).toBeLessThanOrEqual(1)
  else expect(headerGap).toBeGreaterThanOrEqual(0)
  if (!mobile) expect(headerGap).toBeLessThanOrEqual(24)
  const goalTitle = overview.session?.activeGoal?.title
  if (goalTitle) await expect(workspace).toContainText(goalTitle)
  await assertNoHorizontalOverflow(page)
  await capture(page, testInfo, 'canvas')

  if (mobile) await page.getByRole('button', { name: /^计划/ }).last().click()
  else await page.getByRole('tab', { name: /^学习计划/ }).click()
  await expect(workspace).toContainText(/学习计划|行动计划/)
  await assertNoHorizontalOverflow(page)

  if (mobile) await page.getByRole('button', { name: /^验证/ }).last().click()
  else await page.getByRole('tab', { name: /^能力验证/ }).click()
  await expect(workspace).toContainText('能力画布')
  await expect(workspace).toContainText(/选择验证方式|尚未选择可验证节点/)
  await assertNoHorizontalOverflow(page)

  if (mobile) await page.getByRole('button', { name: '版本', exact: true }).last().click()
  else await page.getByRole('tab', { name: '版本记录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '规划版本记录', exact: true })).toBeVisible()
  await expect(workspace).toContainText('版本时间线')
  await assertNoHorizontalOverflow(page)

  if (!mobile) {
    const historyScroll = await page.evaluate(() => {
      const host = document.querySelector<HTMLElement>('.workbench-view-host')
      const header = document.querySelector<HTMLElement>('.workbench-shell-header')
      const hostTop = host ? host.getBoundingClientRect().top + window.scrollY : 0
      const maxScroll = Math.max(0, document.documentElement.scrollHeight - innerHeight)
      const target = Math.min(hostTop + 320, maxScroll)
      window.scrollTo(0, target)
      const headerRect = header?.getBoundingClientRect()
      const stickyTop = header ? Number.parseFloat(getComputedStyle(header).top) : 0
      const headerOffset = headerRect
        ? Math.max(0, (Number.isFinite(stickyTop) ? stickyTop : Math.max(0, headerRect.top)) + headerRect.height)
        : 0
      return { visibleOffset: window.scrollY + headerOffset - hostTop }
    })
    expect(historyScroll.visibleOffset).toBeGreaterThan(0)
    await page.getByRole('tab', { name: /^能力验证/ }).click()
    await page.getByRole('tab', { name: '版本记录', exact: true }).click()
    await expect.poll(async () => {
      const restored = await page.evaluate(() => {
        const host = document.querySelector<HTMLElement>('.workbench-view-host')
        const header = document.querySelector<HTMLElement>('.workbench-shell-header')
        const hostTop = host ? host.getBoundingClientRect().top + window.scrollY : 0
        const headerRect = header?.getBoundingClientRect()
        const stickyTop = header ? Number.parseFloat(getComputedStyle(header).top) : 0
        const headerOffset = headerRect
          ? Math.max(0, (Number.isFinite(stickyTop) ? stickyTop : Math.max(0, headerRect.top)) + headerRect.height)
          : 0
        const visibleOffset = window.scrollY + headerOffset - hostTop
        const maxVisibleOffset = Math.max(
          0,
          document.documentElement.scrollHeight - innerHeight + headerOffset - hostTop,
        )
        return { visibleOffset, maxVisibleOffset }
      })
      return Math.abs(restored.visibleOffset - Math.min(historyScroll.visibleOffset, restored.maxVisibleOffset))
    }).toBeLessThanOrEqual(5)
  }

  await page.goto('/career-planning')
  await expect(page.getByRole('heading', { name: '能力画布总览', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /新建能力画布/ }).first()).toBeVisible()
  await expect(page.getByText('可复用节点')).toHaveCount(0)
  await expect(page.getByText('更换目标职业')).toHaveCount(0)
  await assertNoHorizontalOverflow(page)
  await capture(page, testInfo, 'canvas-overview')
  expect(runtimeErrors).toEqual([])
})

test('职业规划减少动态效果时立即切换且保留可访问状态', async ({ page }, testInfo) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await loginAndOpenActiveSession(page)
  const mobile = testInfo.project.name.startsWith('mobile-')
  if (mobile) {
    await page.getByRole('button', { name: /^计划/ }).last().click()
  } else {
    const tab = page.getByRole('tab', { name: /^学习计划/ })
    await tab.click()
    await expect(tab).toHaveAttribute('aria-selected', 'true')
  }
  const motion = await page.locator('.workbench-view-host').evaluate(element => {
    const panel = element.firstElementChild
    const indicator = document.querySelector<HTMLElement>('.workbench-tab-indicator')
    return {
      panelDuration: panel ? getComputedStyle(panel).transitionDuration : '',
      indicatorDuration: indicator ? getComputedStyle(indicator).transitionDuration : '',
    }
  })
  expect(motion.panelDuration === '' || motion.panelDuration === '0s').toBeTruthy()
  if (!mobile) expect(motion.indicatorDuration).toBe('0s')
  await assertNoHorizontalOverflow(page)
})

test('目标明确的求职者可创建并恢复 KNOWN_TARGET 会话', async ({ page }, testInfo) => {
  await registerFreshSeeker(page, testInfo)
  await page.goto('/career-planning/new')
  await deferWhatsNew(page)
  await capture(page, testInfo, 'welcome-double-entry')

  await page.getByRole('button', { name: '填写目标职业' }).click()
  const taxonomyTrigger = page.getByRole('button', { name: /从岗位库中选择目标岗位/ })
  await taxonomyTrigger.click()
  const picker = page.getByRole('dialog', { name: '选择目标岗位' })
  await expect(picker).toBeVisible()
  await picker.getByLabel('搜索岗位').fill('后端开发')
  const target = picker.getByRole('button', { name: /后端开发/ }).first()
  await expect(target).toBeVisible()
  await target.click()
  await expect(page.locator('.job-taxonomy-trigger')).toContainText('后端开发')

  await page.getByRole('button', { name: '确认并开始' }).click()
  await expect(page).toHaveURL(/\/career-planning\/[0-9a-f-]+$/)
  await expect(page.getByRole('heading', { name: '基础信息' })).toBeVisible()
  const sessionId = new URL(page.url()).pathname.split('/').pop()!
  const response = await page.request.get(`/api/v1/career-planning/sessions/${encodeURIComponent(sessionId)}`)
  expect(response.ok(), `目标职业会话读取失败：${response.status()}`).toBeTruthy()
  const created = await data<CareerPlanningSession>(response)
  expect(created.entryMode).toBe('KNOWN_TARGET')
  expect(created.profile.objectiveTaxonomyId).toBeTruthy()

  await page.reload()
  await expect(page).toHaveURL(new RegExp(`/career-planning/${sessionId}$`))
  await expect(page.getByRole('heading', { name: '基础信息' })).toBeVisible()
  await assertNoHorizontalOverflow(page)
  await capture(page, testInfo, 'known-target-profile')
})

test('窄卡片中的三级岗位选择器保持稳定并可完成选择', async ({ page }, testInfo) => {
  const mobile = testInfo.project.name.startsWith('mobile-')
  const runtimeErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') runtimeErrors.push(`console: ${message.text()}`)
  })
  page.on('pageerror', error => runtimeErrors.push(`page: ${error.message}`))
  page.on('response', response => {
    if (response.url().includes('/api/') && response.status() >= 500) {
      runtimeErrors.push(`api: ${response.status()} ${response.url()}`)
    }
  })
  if (!mobile) await page.setViewportSize({ width: 1015, height: 898 })
  await registerFreshSeeker(page, testInfo)
  await page.goto('/career-planning/new')
  await deferWhatsNew(page)

  await page.getByRole('button', { name: '填写目标职业' }).click()
  const trigger = page.getByRole('button', { name: /从岗位库中选择目标岗位/ })
  await trigger.scrollIntoViewIfNeeded()
  const entryHeadingBox = await page.locator('.cp-entry').last().locator(':scope > div').first().boundingBox()
  expect(entryHeadingBox).not.toBeNull()
  expect(entryHeadingBox!.width).toBeGreaterThanOrEqual(200)
  const consent = page.locator('.cp-consent')
  const consentBefore = await consent.boundingBox()
  expect(consentBefore).not.toBeNull()
  await capture(page, testInfo, 'job-taxonomy-before')

  await trigger.click()
  const picker = page.getByRole('dialog', { name: '选择目标岗位' })
  await expect(picker).toBeVisible()
  const roles = picker.locator('.job-taxonomy-roles')
  const target = roles.getByRole('option').first()
  await expect(target).toBeVisible()

  const consentAfter = await consent.boundingBox()
  const pickerBox = await picker.boundingBox()
  const rolesBox = await roles.boundingBox()
  const targetBox = await target.boundingBox()
  expect(consentAfter).not.toBeNull()
  expect(pickerBox).not.toBeNull()
  expect(rolesBox).not.toBeNull()
  expect(targetBox).not.toBeNull()
  expect(Math.abs(consentAfter!.y - consentBefore!.y)).toBeLessThanOrEqual(2)
  expect(pickerBox!.width).toBeGreaterThanOrEqual(mobile ? 350 : 620)
  expect(rolesBox!.width).toBeGreaterThanOrEqual(mobile ? 320 : 260)
  expect(targetBox!.width).toBeGreaterThanOrEqual(100)
  await assertNoHorizontalOverflow(page)
  await capture(page, testInfo, 'job-taxonomy-picker')

  const targetName = (await target.innerText()).trim()
  await target.click()
  await expect(picker).toBeHidden()
  await expect(page.locator('.job-taxonomy-trigger')).toContainText(targetName)
  await expect(page.getByRole('button', { name: '确认并开始' })).toBeEnabled()
  await assertNoHorizontalOverflow(page)

  await page.goto('/career-planning')
  await page.getByRole('button', { name: /新建能力画布/ }).first().click()
  const newCanvasDialog = page.getByRole('dialog', { name: '新建能力画布' })
  const modalTrigger = newCanvasDialog.locator('.job-taxonomy-trigger')
  await modalTrigger.click()
  const modalPicker = page.getByRole('dialog', { name: '选择目标岗位' })
  await expect(modalPicker).toBeVisible()
  const modalTarget = modalPicker.getByRole('option').first()
  const modalTargetName = (await modalTarget.innerText()).trim()
  await modalTarget.click()
  await expect(modalPicker).toBeHidden()
  await expect(modalTrigger).toContainText(modalTargetName)
  await expect(newCanvasDialog.getByRole('button', { name: '创建目标画布' })).toBeEnabled()
  await assertNoHorizontalOverflow(page)
  expect(runtimeErrors).toEqual([])
})

test('全新求职者可完成 AI 画像草稿恢复与空资料授权', async ({ page }, testInfo) => {
  test.setTimeout(90_000)
  const runtimeErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') runtimeErrors.push(`console: ${message.text()}`)
  })
  page.on('pageerror', error => runtimeErrors.push(`page: ${error.message}`))
  page.on('response', response => {
    if (response.url().includes('/api/') && response.status() >= 500) {
      runtimeErrors.push(`api: ${response.status()} ${response.url()}`)
    }
  })

  await registerFreshSeeker(page, testInfo)
  await page.goto('/career-planning/new')
  await deferWhatsNew(page)
  await expect(page.getByRole('heading', { name: '先确定职业方向，再生成你的能力树' })).toBeVisible()
  await expect(page.getByRole('button', { name: '开始职业方向判定' })).toBeVisible()
  await page.getByRole('button', { name: '开始职业方向判定' }).click()
  await expect(page.getByRole('heading', { name: '基础信息' })).toBeVisible()
  await chooseSelect(page, '当前身份', '学生')
  await chooseSelect(page, '最高学历', '本科')
  await chooseSelect(page, '工作年限', '暂无正式经验')
  await chooseSelect(page, '每周学习时间', '5~10小时')
  await page.getByPlaceholder('例如：软件工程').fill('计算机科学与技术')
  await page.getByPlaceholder('例如：2026').fill('2027')
  await expect(page.getByRole('status')).toContainText('草稿已保存', { timeout: 10_000 })
  await capture(page, testInfo, 'profile-basics')

  await page.reload()
  await expect(page.getByRole('combobox', { name: '当前身份' })).toContainText('学生')
  await expect(page.getByRole('combobox', { name: '最高学历' })).toContainText('本科')
  await expect(page.getByPlaceholder('例如：软件工程')).toHaveValue('计算机科学与技术')
  await expect(page.getByPlaceholder('例如：2026')).toHaveValue('2027')

  await page.getByRole('tab', { name: '2 技能与经历' }).click()
  await page.getByRole('button', { name: /选择专业技能/ }).click()
  const skillDialog = page.getByRole('dialog', { name: '选择专业技能' })
  await expect(skillDialog).toBeVisible()
  await capture(page, testInfo, 'skill-picker')
  await skillDialog.getByRole('button', { name: '取消' }).click()
  await page.getByPlaceholder('只写真实发生的经历，建议包含你的职责、行动和结果。').fill(
    '在课程项目中负责 Spring Boot 接口开发，完成参数校验、数据库设计和自动化测试。',
  )
  await page.getByRole('tab', { name: '3 个人倾向与限制' }).click()
  await page.getByPlaceholder('例如：上海、杭州').fill('杭州')
  await chooseSelect(page, '办公方式', '混合办公')
  await page.getByPlaceholder('例如：未来 3 个月完成方向验证，并形成可展示的项目证据').fill(
    '未来三个月完成后端方向验证并形成可复核的项目证据。',
  )
  await page.getByRole('button', { name: '选择资料授权' }).click()

  const evidenceDialog = page.getByRole('dialog', { name: '选择本次规划可使用的资料' })
  await expect(evidenceDialog).toBeVisible()
  await expect(evidenceDialog).toContainText('暂无可授权的确认资料')
  await capture(page, testInfo, 'evidence-authorization')
  await evidenceDialog.getByRole('button', { name: '确认授权 0 项' }).click()
  await expect(page.getByText('资料范围已保存')).toBeVisible()
  await page.getByRole('button', { name: '直接确认画像' }).click()
  await expect(page.getByRole('heading', { name: '确认职业画像' })).toBeVisible()
  await expect(page.locator('.cp-confirm-basics').getByText('计算机科学与技术')).toBeVisible()
  await expect(page.getByText(/Spring Boot 接口开发/).first()).toBeVisible()
  await assertNoHorizontalOverflow(page)
  await capture(page, testInfo, 'profile-confirmation')
  expect(runtimeErrors).toEqual([])
})

test('AI 访谈可生成方向并完成收藏对比和目标双确认', async ({ page }, testInfo) => {
  test.setTimeout(240_000)
  const runtimeErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') runtimeErrors.push(`console: ${message.text()}`)
  })
  page.on('pageerror', error => runtimeErrors.push(`page: ${error.message}`))
  page.on('response', response => {
    if (response.url().includes('/api/') && response.status() >= 500) {
      runtimeErrors.push(`api: ${response.status()} ${response.url()}`)
    }
  })

  await registerFreshSeeker(page, testInfo)
  await page.goto('/career-planning/new')
  await deferWhatsNew(page)
  await page.getByRole('button', { name: '开始职业方向判定' }).click()
  await chooseSelect(page, '当前身份', '应届生')
  await chooseSelect(page, '最高学历', '本科')
  await chooseSelect(page, '工作年限', '暂无正式经验')
  await chooseSelect(page, '每周学习时间', '10~15小时')
  await page.getByPlaceholder('例如：软件工程').fill('计算机科学与技术')
  await page.getByPlaceholder('例如：2026').fill('2026')

  await page.getByRole('tab', { name: '2 技能与经历' }).click()
  await page.getByPlaceholder('只写真实发生的经历，建议包含你的职责、行动和结果。').fill(
    '在课程项目中使用 Java、Spring Boot 和 MySQL 开发订单接口，负责参数校验、数据库表设计和接口自动化测试。',
  )
  await page.getByRole('tab', { name: '3 个人倾向与限制' }).click()
  await page.getByPlaceholder('例如：上海、杭州').fill('杭州')
  await page.getByPlaceholder('例如：希望从机械方向转向软件开发，同时保留行业经验').fill('希望从事 Java 后端开发。')
  await page.getByPlaceholder('例如：未来 3 个月完成方向验证，并形成可展示的项目证据').fill('三个月内补齐后端工程能力并完成可复核项目。')

  await page.getByRole('button', { name: '选择资料授权' }).click()
  const evidenceDialog = page.getByRole('dialog', { name: '选择本次规划可使用的资料' })
  await expect(evidenceDialog).toBeVisible()
  await evidenceDialog.getByRole('button', { name: '确认授权 0 项' }).click()
  await page.getByRole('button', { name: 'AI 补充访谈' }).click()

  const answer = page.getByPlaceholder('输入真实情况；不确定时可以跳过')
  await expect(answer).toBeVisible({ timeout: 90_000 })
  await capture(page, testInfo, 'ai-interview')
  let answeredOneQuestion = false
  for (let guard = 0; guard < 10; guard += 1) {
    if (await page.getByRole('heading', { name: '确认职业画像' }).isVisible()) break
    await expect(answer).toBeVisible({ timeout: 20_000 })
    const progress = await page.locator('.cp-question-card > header > span').innerText()
    const [current, total] = progress.split('/').map(value => Number.parseInt(value.trim(), 10))
    if (!answeredOneQuestion) {
      await answer.fill('课程项目由三人协作，我负责后端接口、MySQL 数据表和自动化测试；没有虚构用户规模、性能指标或上线结果。')
      answeredOneQuestion = true
      await page.getByRole('button', { name: current === total ? '提交并核对画像' : '保存并继续' }).click()
    } else {
      await page.getByRole('button', { name: '跳过这一题' }).click()
    }
    if (current === total) break
    await expect(page.locator('.cp-question-card > header > span')).not.toHaveText(progress, { timeout: 20_000 })
  }

  await expect(page.getByRole('heading', { name: '确认职业画像' })).toBeVisible({ timeout: 30_000 })
  await expect(page.locator('.cp-confirm-sources').filter({ hasText: 'AI 访谈回答' }).first()).toBeVisible()
  await page.getByRole('button', { name: '确认画像并进入推荐' }).click()
  await expect(page.getByRole('button', { name: '生成职业方向 · 1 次' })).toBeVisible()
  await page.getByRole('button', { name: '生成职业方向 · 1 次' }).click()

  const recommendations = page.locator('.cp-recommendation-list .cp-recommendation')
  await expect.poll(() => recommendations.count(), { timeout: 120_000 }).toBeGreaterThanOrEqual(3)
  await assertNoHorizontalOverflow(page)
  if (testInfo.project.name.startsWith('desktop-')) {
    const recommendationBodyWidth = await recommendations.first()
      .locator('.cp-recommendation__body')
      .evaluate(element => element.getBoundingClientRect().width)
    const factWidths = await recommendations.first()
      .locator('.cp-recommendation__facts > span')
      .evaluateAll(elements => elements.map(element => element.getBoundingClientRect().width))
    expect(recommendationBodyWidth, '桌面推荐正文不能被操作区压成逐字换行').toBeGreaterThanOrEqual(300)
    expect(factWidths[0], '推荐依据列必须保持可读宽度').toBeGreaterThanOrEqual(100)
    expect(factWidths[1], '能力缺口列必须保持可读宽度').toBeGreaterThanOrEqual(90)
    expect(factWidths[2], '证据数量列必须保持可读宽度').toBeGreaterThanOrEqual(60)
  }
  await capture(page, testInfo, 'recommendations')

  const firstRecommendation = recommendations.first()
  await firstRecommendation.getByRole('button', { name: '详情' }).click()
  const detailDialog = page.getByRole('dialog', { name: '职业方向详情' })
  await expect(detailDialog).toContainText('推荐依据')
  await expect(detailDialog).toContainText('能力缺口')
  await capture(page, testInfo, 'recommendation-detail')
  await detailDialog.locator('.modal__foot').getByRole('button', { name: '关闭' }).click()

  await firstRecommendation.getByRole('button', { name: '收藏' }).click()
  await expect(firstRecommendation.getByRole('button', { name: '已收藏' })).toBeVisible()
  await firstRecommendation.getByRole('button', { name: '对比' }).click()
  await recommendations.nth(1).getByRole('button', { name: '对比' }).click()
  await page.getByRole('button', { name: '对比 2/3' }).click()
  const compareDialog = page.getByRole('dialog', { name: '职业方向对比' })
  await expect(compareDialog).toContainText('2 / 3')
  await compareDialog.getByRole('button', { name: '返回推荐列表' }).click()

  await firstRecommendation.getByRole('button', { name: '设为目标' }).click()
  const goalDialog = page.getByRole('dialog', { name: '确认目标职业' })
  await expect(goalDialog).toContainText('明确的用户确认')
  await capture(page, testInfo, 'goal-confirmation')
  await goalDialog.getByRole('checkbox').check()
  await goalDialog.getByRole('button', { name: '确认目标并创建画布' }).click()
  await expect(page.locator('.career-canvas-shell')).toBeVisible({ timeout: 30_000 })
  await expect(page.getByRole('button', { name: 'AI 优化', exact: true })).toBeVisible()
  await assertNoHorizontalOverflow(page)
  expect(runtimeErrors).toEqual([])
})

test('完整执行闭环覆盖能力树、提案、计划、验证、版本恢复和独立画布新建', async ({ page }, testInfo) => {
  test.setTimeout(600_000)
  page.setDefaultTimeout(20_000)
  const mobile = testInfo.project.name.startsWith('mobile-')
  const runtimeErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') runtimeErrors.push(`console: ${message.text()}`)
  })
  page.on('pageerror', error => runtimeErrors.push(`page: ${error.message}`))
  page.on('response', response => {
    if (response.url().includes('/api/') && response.status() >= 500) {
      runtimeErrors.push(`api: ${response.status()} ${response.url()}`)
    }
  })

  const sessionId = await createDirectCareerGoal(page, testInfo)
  let session = await fetchSession(page, sessionId)
  expect(session.canvas?.nodes).toHaveLength(1)
  const rootVersion = session.canvas!.version

  await page.getByRole('button', { name: '生成完整能力树', exact: true }).click()
  await expect.poll(async () => (await fetchSession(page, sessionId)).canvas?.nodes.length ?? 0, {
    timeout: 180_000,
    message: '完整能力树异步任务没有形成正式节点',
  }).toBeGreaterThan(4)
  await expect(page.getByRole('button', { name: 'AI 优化', exact: true })).toBeEnabled({ timeout: 30_000 })
  await expect.poll(() => page.locator('.flow-card').count()).toBeGreaterThan(4)
  session = await fetchSession(page, sessionId)
  expect(session.canvas!.version).toBeGreaterThan(rootVersion)
  await capture(page, testInfo, 'complete-canvas')

  const beforeEditVersion = session.canvas!.version
  if (mobile) {
    const mobileDomain = page.getByRole('button', { name: /查看.+详情，能力域/ }).first()
    await expect(mobileDomain).toBeVisible()
    await mobileDomain.click()
  } else {
    await page.locator('.flow-card--domain').first().click()
  }
  const nodeDrawer = page.getByLabel('能力节点详情')
  await expect(nodeDrawer).toBeVisible()
  await capture(page, testInfo, 'node-detail')
  await nodeDrawer.getByRole('button', { name: '编辑', exact: true }).click()
  await nodeDrawer.getByLabel('用户备注').fill('E2E 合成备注：该节点需要通过代码、测试和复盘记录共同验证。')
  await nodeDrawer.getByRole('button', { name: '保存为新版本', exact: true }).click()
  await expect.poll(async () => (await fetchSession(page, sessionId)).canvas?.version ?? 0).toBeGreaterThan(beforeEditVersion)
  await expect(nodeDrawer).toContainText('E2E 合成备注')
  await nodeDrawer.getByRole('button', { name: '关闭节点详情' }).click()

  session = await fetchSession(page, sessionId)
  const beforeProposalVersion = session.canvas!.version
  await page.getByRole('button', { name: 'AI 优化', exact: true }).click()
  const proposalDialog = page.getByRole('dialog', { name: 'AI 画布差异提案' })
  await proposalDialog.getByPlaceholder('例如：补强 Spring Boot 云原生工程能力，并增加可验证的项目任务').fill(
    '仅基于现有 Java 后端目标补充一项容器化交付能力和一项可验证任务，不要删除用户节点。',
  )
  await proposalDialog.getByRole('button', { name: '生成差异提案' }).click()
  await expect(proposalDialog.locator('.proposal-items article').first()).toBeVisible({ timeout: 180_000 })
  expect((await fetchSession(page, sessionId)).canvas!.version).toBe(beforeProposalVersion)
  await capture(page, testInfo, 'proposal-before-decision')
  await proposalDialog.getByRole('button', { name: '全部接受' }).click()
  await proposalDialog.getByRole('button', { name: '应用已确认差异' }).click()
  await expect(proposalDialog).toBeHidden({ timeout: 30_000 })
  await expect.poll(async () => (await fetchSession(page, sessionId)).canvas?.version ?? 0).toBeGreaterThan(beforeProposalVersion)

  await page.getByRole('button', { name: '生成计划', exact: true }).click()
  const planDialog = page.getByRole('dialog', { name: '行动计划' })
  await capture(page, testInfo, 'plan-setup')
  await planDialog.getByRole('button', { name: '4 周 快速验证', exact: true }).click()
  await planDialog.getByRole('button', { name: '生成 4 周计划' }).click()
  await expect(page.locator('.learning-plan')).toBeVisible({ timeout: 30_000 })
  const firstTask = page.locator('.task-list article').first()
  await expect(firstTask).toBeVisible()
  await firstTask.getByRole('button', { name: '添加证据' }).click()
  const evidenceDialog = page.getByRole('dialog', { name: '添加能力证据' })
  await evidenceDialog.getByPlaceholder('写明你完成的工作、验证方式和可复核结果').fill(
    '合成验收证据：完成对应代码练习，运行自动化测试，并记录输入、输出和失败边界。',
  )
  await evidenceDialog.getByRole('button', { name: '保存并关联' }).click()
  await expect(evidenceDialog).toBeHidden()

  let executionResponse = await page.request.get(`/api/v1/career-planning/sessions/${encodeURIComponent(sessionId)}/execution`)
  expect(executionResponse.ok()).toBeTruthy()
  let execution = await data<CareerExecution>(executionResponse)
  const validationNodeId = execution.activePlan?.tasks[0]?.nodeId
  expect(validationNodeId).toBeTruthy()

  await firstTask.getByRole('button', { name: '完成任务' }).click()
  await expect(firstTask.getByRole('button', { name: '恢复任务' })).toBeVisible({ timeout: 20_000 })
  await expect(firstTask.getByRole('button', { name: '去验证' })).toBeVisible({ timeout: 20_000 })
  await capture(page, testInfo, 'learning-plan-with-evidence')
  await firstTask.getByRole('button', { name: '去验证' }).click()

  await expect(page.locator('.validation-workspace')).toBeVisible()
  await page.getByRole('button', { name: /实践任务/ }).click()
  await page.getByPlaceholder('说明你完成了什么、如何验证，以及有哪些结果可以复核').fill(
    '我基于现有课程项目完成了对应代码调整，执行了自动化测试并检查异常输入。证据只证明本次练习，不宣称线上规模或性能提升。',
  )
  await page.getByRole('button', { name: '提交证据并评估' }).click()
  const evaluation = page.locator('.evaluation-result')
  await expect(evaluation).toBeVisible({ timeout: 180_000 })
  session = await fetchSession(page, sessionId)
  expect(session.canvas?.nodes.find(node => node.logicalNodeId === validationNodeId)?.status).not.toBe('MASTERED')
  const evaluationPassed = await evaluation.getByText('评估通过', { exact: true }).isVisible()
  await capture(page, testInfo, 'validation-before-user-confirmation')
  await evaluation.getByRole('button', { name: '确认评估结果' }).click()
  await expect(evaluation).toContainText('用户决定已记录', { timeout: 30_000 })
  await expect.poll(async () => {
    const current = await fetchSession(page, sessionId)
    return current.canvas?.nodes.find(node => node.logicalNodeId === validationNodeId)?.status
  }).toBe(evaluationPassed ? 'MASTERED' : 'PENDING_VALIDATION')

  session = await fetchSession(page, sessionId)
  const beforeRestoreVersion = session.canvas!.version
  if (mobile) await page.getByRole('button', { name: '版本', exact: true }).last().click()
  else await page.getByRole('tab', { name: '版本记录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '规划版本记录', exact: true })).toBeVisible()
  const previousVersion = page.locator('.version-timeline li:not(.current)').first()
  await previousVersion.getByRole('button').click()
  await expect(page.getByRole('button', { name: '恢复为新版本' })).toBeVisible()
  await capture(page, testInfo, 'version-comparison')
  await page.getByRole('button', { name: '恢复为新版本' }).click()
  await expect.poll(async () => (await fetchSession(page, sessionId)).canvas?.version ?? 0).toBeGreaterThan(beforeRestoreVersion)

  session = await fetchSession(page, sessionId)
  const oldGoalId = session.activeGoal?.id
  expect(oldGoalId).toBeTruthy()
  await page.goto('/career-planning')
  await expect(page.getByRole('heading', { name: '能力画布总览', exact: true })).toBeVisible()
  await page.getByRole('button', { name: /新建能力画布/ }).first().click()
  const newCanvasDialog = page.getByRole('dialog', { name: '新建能力画布' })
  await expect(newCanvasDialog).toBeVisible()
  await expect(newCanvasDialog).toContainText('新画布不会复制旧节点')
  await newCanvasDialog.getByRole('button', { name: /从岗位库中选择目标岗位/ }).click()
  const picker = page.getByRole('dialog', { name: '选择目标岗位' })
  await picker.getByLabel('搜索岗位').fill('后端开发')
  const target = picker.getByRole('button', { name: /后端开发/ }).first()
  await expect(target).toBeVisible()
  await target.click()
  const createResponsePromise = page.waitForResponse(response => response.url().endsWith('/career-planning/canvases')
    && response.request().method() === 'POST')
  await newCanvasDialog.getByRole('button', { name: '创建目标画布' }).click()
  const createResponse = await createResponsePromise
  expect(createResponse.ok(), `新建能力画布失败：${createResponse.status()}`).toBeTruthy()
  const createdCanvas = await data<CareerPlanningSession>(createResponse)
  expect(createdCanvas.id).not.toBe(sessionId)
  expect(createdCanvas.activeGoal?.id).not.toBe(oldGoalId)
  expect(createdCanvas.canvas?.version).toBe(1)
  expect(createdCanvas.canvas?.nodes).toHaveLength(1)
  await expect(page).toHaveURL(new RegExp(`/career-planning/${createdCanvas.id}`))
  await expect(page.getByRole('button', { name: '生成完整能力树' }).first()).toBeVisible()
  await assertNoHorizontalOverflow(page)
  await capture(page, testInfo, 'new-independent-canvas')
  expect(runtimeErrors).toEqual([])
})
