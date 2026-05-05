/**
 * One-shot screenshot capture for the user manual.
 *
 * Pre-requisites:
 *   1. `npm run dev` is running on http://localhost:5173
 *   2. VITE_USE_MSW=true (frontend/.env.local already has this)
 *
 * Usage:
 *   npx tsx scripts/capture-screenshots.ts
 *
 * Output:
 *   ../docs/user-manual/screenshots/{desktop,mobile}/<step>.png
 */
import { chromium, type Browser, type BrowserContext, type Page } from 'playwright'
import * as fs from 'node:fs'
import * as path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const BASE_URL = process.env.SHOTS_BASE_URL ?? 'http://localhost:5173'
const OUT_DIR = path.resolve(__dirname, '../../docs/user-manual/screenshots')
const ACCOUNT = process.env.SHOTS_ACCOUNT ?? 'manager.wang'
const PASSWORD = process.env.SHOTS_PASSWORD ?? 'Manager@123456789'

type ShotStep = {
  name: string
  url: string
  beforeShot?: (page: Page) => Promise<void>
  fullPage?: boolean
}

const desktopSteps: ShotStep[] = [
  { name: '01-login',                 url: '/login',                       fullPage: true },
  { name: '02-daily-board',           url: '/',                            fullPage: true },
  { name: '03-projects-list',         url: '/projects',                    fullPage: true },
  { name: '04-project-detail',        url: '/projects/project-1',          fullPage: true },
  { name: '05-task-detail',           url: '/tasks/task-1',                fullPage: true },
  { name: '06-action-requests',       url: '/action-requests',             fullPage: true },
  { name: '07-action-request-dispatch', url: '/action-requests/dispatch',  fullPage: true },
  { name: '08-organization-tree',     url: '/organizations',               fullPage: true },
  { name: '09-group-list',            url: '/groups',                      fullPage: true },
  { name: '10-group-detail',          url: '/groups/group-1',              fullPage: true },
  { name: '11-templates',             url: '/templates',                   fullPage: true },
  { name: '12-profile',               url: '/profile',                     fullPage: true },
]

const mobileSteps: ShotStep[] = [
  { name: '01-login',          url: '/login',           fullPage: true },
  { name: '02-daily-board',    url: '/',                fullPage: true },
  { name: '03-task-detail',    url: '/tasks/task-1',    fullPage: true },
  { name: '04-projects-list',  url: '/projects',        fullPage: true },
]

async function login(page: Page): Promise<void> {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' })
  await page.fill('input[name="accountName"], input[type="text"], input[placeholder*="帳號" i], input[placeholder*="account" i]', ACCOUNT)
  await page.fill('input[type="password"]', PASSWORD)
  // Some login forms have an orgCode field (real backend); fill if present
  const orgCodeInput = page.locator('input[name="orgCode"], input[placeholder*="orgCode" i], input[placeholder*="廠" i]')
  if (await orgCodeInput.count() > 0) {
    try { await orgCodeInput.first().fill('fab-alpha') } catch { /* ignore */ }
  }
  await page.click('button[type="submit"]')
  await page.waitForURL(u => !u.pathname.endsWith('/login'), { timeout: 10_000 })
}

async function captureSteps(
  context: BrowserContext,
  steps: ShotStep[],
  outSubdir: string,
  options: { loginFirst: boolean }
): Promise<void> {
  const dir = path.join(OUT_DIR, outSubdir)
  fs.mkdirSync(dir, { recursive: true })

  const page = await context.newPage()
  page.on('pageerror', err => console.warn(`  ⚠️  pageerror: ${err.message}`))

  // Capture the unauthenticated /login screen first (if requested), THEN log in once
  // and walk the remaining authenticated steps.
  const loginStep = steps.find(s => s.url === '/login')
  const restSteps = steps.filter(s => s.url !== '/login')

  if (loginStep) {
    console.log(`[${outSubdir}] ${loginStep.name} ← ${loginStep.url}`)
    await page.goto(`${BASE_URL}${loginStep.url}`, { waitUntil: 'networkidle' }).catch(() => undefined)
    await page.waitForTimeout(800)
    await page.screenshot({
      path: path.join(dir, `${loginStep.name}.png`),
      fullPage: loginStep.fullPage ?? true,
    })
  }

  if (options.loginFirst) {
    console.log(`[${outSubdir}] logging in as ${ACCOUNT} ...`)
    await login(page)
  }

  for (const step of restSteps) {
    console.log(`[${outSubdir}] ${step.name} ← ${step.url}`)
    await page.goto(`${BASE_URL}${step.url}`, { waitUntil: 'networkidle' }).catch(() => undefined)
    await page.waitForTimeout(800)
    if (step.beforeShot) await step.beforeShot(page)
    await page.screenshot({
      path: path.join(dir, `${step.name}.png`),
      fullPage: step.fullPage ?? true,
    })
  }

  await page.close()
}

async function main(): Promise<void> {
  fs.mkdirSync(OUT_DIR, { recursive: true })
  let browser: Browser | null = null
  try {
    browser = await chromium.launch({ headless: true })

    // Desktop
    const desktopCtx = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      locale: 'zh-TW',
      timezoneId: 'Asia/Taipei',
      deviceScaleFactor: 1,
    })
    await captureSteps(desktopCtx, desktopSteps, 'desktop', { loginFirst: true })
    await desktopCtx.close()

    // Mobile (iPhone 12 Pro size)
    const mobileCtx = await browser.newContext({
      viewport: { width: 390, height: 844 },
      locale: 'zh-TW',
      timezoneId: 'Asia/Taipei',
      deviceScaleFactor: 2,
      isMobile: true,
      hasTouch: true,
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
    })
    await captureSteps(mobileCtx, mobileSteps, 'mobile', { loginFirst: true })
    await mobileCtx.close()

    console.log(`\nDone. Screenshots written to ${OUT_DIR}`)
  } finally {
    await browser?.close()
  }
}

main().catch(err => {
  console.error(err)
  process.exit(1)
})
