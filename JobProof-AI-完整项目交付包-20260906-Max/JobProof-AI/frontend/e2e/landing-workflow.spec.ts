import { expect, test } from '@playwright/test'

test('desktop workflow fills four columns and aligns each marker with its card', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) <= 920, 'The mobile workflow uses a vertical step layout.')
  await page.goto('/')

  const workflow = page.locator('.landing-workflow')
  await expect(workflow).toBeVisible()

  const geometry = await workflow.evaluate((root) => {
    const rootBox = root.getBoundingClientRect()
    const steps = Array.from(root.querySelectorAll<HTMLElement>('.landing-workflow__step'))

    return {
      columns: getComputedStyle(root).gridTemplateColumns.split(' ').filter(Boolean).length,
      rightGap: rootBox.right - (steps.at(-1)?.getBoundingClientRect().right ?? rootBox.right),
      centerOffsets: steps.map((step) => {
        const marker = step.querySelector<HTMLElement>('.landing-workflow__top span')!.getBoundingClientRect()
        const card = step.querySelector<HTMLElement>('.landing-workflow__visual')!.getBoundingClientRect()
        return Math.abs((marker.left + marker.width / 2) - (card.left + card.width / 2))
      }),
    }
  })

  expect(geometry.columns).toBe(4)
  expect(Math.abs(geometry.rightGap)).toBeLessThanOrEqual(1)
  geometry.centerOffsets.forEach((offset) => expect(offset).toBeLessThanOrEqual(1))
})

test('mobile workflow stays vertical without connectors or horizontal overflow', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) > 920, 'The desktop workflow uses connected columns.')
  await page.goto('/')

  const workflow = page.locator('.landing-workflow')
  await expect(workflow).toBeVisible()

  const geometry = await workflow.evaluate((root) => ({
    columns: getComputedStyle(root).gridTemplateColumns.split(' ').filter(Boolean).length,
    stepCount: root.querySelectorAll('.landing-workflow__step').length,
    connectorDisplays: Array.from(root.querySelectorAll<HTMLElement>('.landing-workflow__top i'))
      .map((connector) => getComputedStyle(connector).display),
    overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
  }))

  expect(geometry.columns).toBe(1)
  expect(geometry.stepCount).toBe(4)
  expect(geometry.connectorDisplays).toEqual(['none', 'none', 'none'])
  expect(geometry.overflow).toBe(0)
})

test('mobile hero and template scene stay fully inside the viewport', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) > 640, 'The desktop scene intentionally uses a wider composition.')
  await page.goto('/')
  await expect(page.locator('.product-scene')).toBeVisible()
  await expect(page.locator('.landing-template-stack')).toBeVisible()

  const geometry = await page.evaluate(() => {
    const rect = (selector: string) => {
      const element = document.querySelector<HTMLElement>(selector)!
      const box = element.getBoundingClientRect()
      return {
        left: box.left,
        right: box.right,
        scrollWidth: element.scrollWidth,
        clientWidth: element.clientWidth,
      }
    }
    return {
      viewport: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
      documentClientWidth: document.documentElement.clientWidth,
      scene: rect('.product-scene'),
      productWindows: [
        rect('.studio-evidence--project'),
        rect('.studio-paper'),
      ],
      stack: rect('.landing-template-stack'),
      sheets: [
        rect('.template-sheet--cyan'),
        rect('.template-sheet--navy'),
        rect('.template-sheet--mono'),
      ],
    }
  })

  expect(geometry.documentWidth).toBe(geometry.documentClientWidth)
  ;[geometry.scene, geometry.stack, ...geometry.productWindows, ...geometry.sheets].forEach((element) => {
    expect(element.left).toBeGreaterThanOrEqual(0)
    expect(element.right).toBeLessThanOrEqual(geometry.viewport)
  })
})
