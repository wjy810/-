import { expect, test } from '@playwright/test'
import { SEEKER_STATE } from './fixtures'

test.use({ storageState: SEEKER_STATE })

test('the app shell persists across navigation and supports the command palette', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) < 960, 'Desktop shell only.')
  await page.goto('/dashboard')
  const sidebar = page.locator('#app-sidebar')
  await expect(sidebar).toBeVisible()
  await sidebar.getByRole('link', { name: '简历', exact: true }).click()
  await expect(page).toHaveURL(/\/resumes$/)
  await expect(sidebar.getByRole('link', { name: '简历', exact: true })).toHaveAttribute('aria-current', 'page')

  await page.keyboard.press('Control+k')
  const palette = page.getByRole('combobox', { name: '搜索页面、操作或简历' })
  await expect(palette).toBeVisible()
  await palette.fill('gwpp')
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/job-match$/)
})

test('mobile navigation opens as a drawer without horizontal overflow', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) >= 960, 'Mobile drawer only.')
  await page.goto('/dashboard')
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow).toBeLessThanOrEqual(1)
  const menu = page.getByTestId('mobile-nav-toggle')
  await expect(menu).toHaveAttribute('aria-expanded', 'false')
  await menu.click()
  await expect(menu).toHaveAttribute('aria-expanded', 'true')
  await expect(page.locator('#app-sidebar').getByRole('link', { name: '资料库' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(menu).toHaveAttribute('aria-expanded', 'false')
  await menu.click()
  await page.locator('.shell__scrim').click({ position: { x: 360, y: 400 } })
  await expect(menu).toHaveAttribute('aria-expanded', 'false')
})

test('dark theme applies before first paint and is remembered', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('jp:preferences', JSON.stringify({ theme: 'dark' })))
  await page.goto('/dashboard')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
})
