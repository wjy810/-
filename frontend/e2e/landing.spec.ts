import { expect, test } from '@playwright/test'

test('landing page presents the hero, four-step flow and opens the auth dialog', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('每一句都站得住')
  await expect(page.locator('.flow__step')).toHaveCount(4)

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow).toBeLessThanOrEqual(1)

  await page.getByRole('link', { name: '免费开始' }).first().click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('heading', { name: '创建你的求职工作台' })).toBeVisible()
  await dialog.getByRole('button', { name: '直接登录' }).click()
  await expect(dialog.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect(page).toHaveURL(/\/$/)
})

test('signing in from the dialog lands on the dashboard', async ({ page }, testInfo) => {
  // A real login counts against the per-account rate limit, so exercise it on two viewports only.
  test.skip(!['desktop-1440', 'mobile-390'].includes(testInfo.project.name), 'Covered by desktop-1440 and mobile-390.')
  await page.goto('/?auth=login')
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('邮箱或手机号').fill('seeker')
  await dialog.getByLabel('密码', { exact: true }).fill('seeker123')
  await dialog.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('好')
})
