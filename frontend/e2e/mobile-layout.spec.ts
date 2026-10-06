import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  const response = await page.request.post('/api/v1/auth/login', {
    data: { email: 'seeker', password: 'seeker123' },
  })
  expect(response.ok()).toBeTruthy()
  await page.goto('/resumes')
})

test('signed-in pages use a full-width mobile shell and drawer navigation', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) > 640, 'The desktop shell keeps a persistent sidebar.')

  const shell = await page.locator('.main').evaluate((element) => {
    const box = element.getBoundingClientRect()
    return {
      left: box.left,
      right: box.right,
      documentWidth: document.documentElement.scrollWidth,
      documentClientWidth: document.documentElement.clientWidth,
    }
  })
  expect(shell.left).toBe(0)
  expect(shell.right).toBe(shell.documentClientWidth)
  expect(shell.documentWidth).toBe(shell.documentClientWidth)

  const sidebar = page.locator('#app-sidebar')
  const menu = page.getByTestId('mobile-nav-toggle')
  await expect(menu).toHaveAttribute('aria-expanded', 'false')
  await expect(sidebar).toHaveCSS('visibility', 'hidden')

  await menu.click()
  await expect(menu).toHaveAttribute('aria-expanded', 'true')
  await expect(sidebar).toHaveCSS('visibility', 'visible')
  await expect(sidebar.getByText('求职资料库', { exact: true })).toBeVisible()
  await expect(sidebar.getByText('账号与安全', { exact: true })).toBeVisible()

  await page.locator('.sidebar-scrim').click({ position: { x: 360, y: 120 } })
  await expect(menu).toHaveAttribute('aria-expanded', 'false')
})

test('mock interview records become readable cards without horizontal scrolling', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) > 760, 'The desktop history uses a table.')
  await page.goto('/mock-interviews')

  const table = page.locator('.mi-history-table')
  await expect(table).toBeVisible()
  const layout = await table.evaluate((element) => {
    const wrapper = element.closest<HTMLElement>('.mi-table-wrap')!
    const firstRow = element.querySelector<HTMLElement>('tbody tr')!
    return {
      tableWidth: element.getBoundingClientRect().width,
      wrapperWidth: wrapper.getBoundingClientRect().width,
      wrapperScrollWidth: wrapper.scrollWidth,
      rowDisplay: getComputedStyle(firstRow).display,
    }
  })

  expect(layout.tableWidth).toBeLessThanOrEqual(layout.wrapperWidth)
  expect(layout.wrapperScrollWidth).toBeLessThanOrEqual(layout.wrapperWidth)
  expect(layout.rowDisplay).toBe('grid')
})
