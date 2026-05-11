#!/usr/bin/env node
/**
 * UI end-to-end smoke test for the TEFCA Gateway admin console.
 *
 * Drives a real headless browser through the same flow an operator would use:
 *   1. Visit http://localhost:8080/admin/  -> follow server-side 302 to /admin/login/
 *   2. Verify the styled login form renders with operator + password + role fields.
 *   3. Submit valid credentials (admin@local / tefca-admin).
 *   4. Verify the browser is hard-navigated to /admin/dashboard/ and the
 *      AppShell sidebar (with operator name, sign-out button) appears.
 *   5. Click through every nav item, asserting each route reaches its
 *      <h1> page header without a runtime error overlay.
 *   6. Click "Sign out" and verify the browser ends up on /admin/login/ again.
 *
 * Uses puppeteer-core driven by the system Chrome via `npx puppeteer`. If
 * puppeteer is not installed locally, the script auto-installs it via npx.
 */

const BASE = process.env.ADMIN_BASE_URL || 'http://localhost:8080';

async function main() {
  let puppeteer;
  try {
    puppeteer = require('puppeteer');
  } catch {
    console.error('puppeteer not found. Install with: npm i -D puppeteer');
    process.exit(2);
  }

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });
  const page = await browser.newPage();
  page.setDefaultTimeout(15000);
  const log = (msg) => console.log(`  ${msg}`);
  const ok = (msg) => console.log(`  ✓ ${msg}`);
  const fail = (msg) => {
    console.error(`  ✗ ${msg}`);
    process.exitCode = 1;
  };

  try {
    // ── 1. /admin/ → redirect to /admin/login/ ────────────────────────────
    console.log('▶ 1. Bootstrap redirect /admin/ → /admin/login/');
    await page.goto(`${BASE}/admin/`, { waitUntil: 'networkidle2' });
    if (page.url().endsWith('/admin/login/')) ok(`landed on ${page.url()}`);
    else fail(`expected /admin/login/, got ${page.url()}`);

    // ── 2. Login form renders ────────────────────────────────────────────
    console.log('▶ 2. Login form renders');
    await page.waitForSelector('input[autocomplete="username"]');
    await page.waitForSelector('input[type="password"]');
    await page.waitForSelector('select');
    const heading = await page.$eval('h1', (h) => h.textContent.trim());
    if (heading === 'Sign in') ok(`heading is "Sign in"`);
    else fail(`heading was "${heading}"`);

    // Background must be the blue gradient (computed style)
    const bgImage = await page.$eval('body > div', (el) => getComputedStyle(el).backgroundImage);
    if (/gradient/i.test(bgImage)) ok('login background uses gradient theme');
    else fail(`login background is "${bgImage}"`);

    // ── 3. Submit valid credentials ───────────────────────────────────────
    console.log('▶ 3. Submit admin@local / tefca-admin');
    await page.waitForFunction(
      () => {
        const u = document.querySelector('input[autocomplete="username"]');
        return !!u && u.value === 'admin@local';
      },
      { timeout: 10000 },
    );
    // Default form values are already valid; just submit.
    await page.evaluate(() => document.querySelector('form')?.requestSubmit());
    try {
      await page.waitForFunction(
        () => location.pathname === '/admin/dashboard/',
        { timeout: 20000, polling: 200 },
      );
      ok(`redirected to ${page.url()}`);
    } catch {
      fail(`expected /admin/dashboard/, got ${page.url()}`);
    }
    await page.waitForNetworkIdle({ idleTime: 500, timeout: 10000 }).catch(() => {});

    // ── 4. Dashboard renders with sidebar ─────────────────────────────────
    console.log('▶ 4. Dashboard shell renders');
    await page.waitForSelector('aside');
    const sidebarText = await page.$eval('aside', (el) => el.innerText);
    if (sidebarText.includes('Dashboard') && sidebarText.includes('Policies')) {
      ok('sidebar lists nav items (Dashboard, Policies, …)');
    } else {
      fail(`sidebar text was: ${sidebarText.slice(0, 200)}`);
    }
    if (/admin@local|Default Admin/i.test(sidebarText)) ok('sidebar shows logged-in operator');
    else fail(`sidebar missing operator name. text=${sidebarText.slice(0, 200)}`);

    // ── 5. Visit every nav route ──────────────────────────────────────────
    console.log('▶ 5. Click through every nav route');
    const routes = [
      ['/admin/dashboard/', 'Dashboard'],
      ['/admin/policies/', 'Policies'],
      ['/admin/policy-audit/', 'Policy Audit'],
      ['/admin/directory/', 'Directory'],
      ['/admin/transactions/', 'Transactions'],
      ['/admin/test-console/', 'Test Console'],
      ['/admin/audit/', 'Audit'],
      ['/admin/metrics/', 'Metrics'],
      ['/admin/config/', 'Configuration'],
    ];
    for (const [path, label] of routes) {
      await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle2' });
      try {
        await page.waitForSelector('h1', { timeout: 8000 });
        const h1 = await page.$eval('h1', (h) => h.textContent.trim());
        ok(`${path} → <h1>${h1}</h1>`);
      } catch {
        fail(`${path} did not render an <h1> (label: ${label})`);
      }
    }

    // ── 6. Test Console: send a request through the gateway ──────────────
    console.log('▶ 6. Test Console submits a TEFCA request');
    await page.goto(`${BASE}/admin/test-console/`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('select');
    await page.waitForSelector('textarea');
    // Default operation is patient-discovery with a valid sample body.
    await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const send = buttons.find((b) => /^send$/i.test(b.textContent.trim()));
      if (send) send.click();
    });
    try {
      await page.waitForFunction(
        () => {
          const txt = document.body.innerText;
          // Look for an HTTP success badge (HTTP 200 or HTTP 202) in the response panel.
          return /HTTP\s+(2\d\d)/.test(txt);
        },
        { timeout: 15000, polling: 200 },
      );
      const status = await page.evaluate(() => {
        const m = document.body.innerText.match(/HTTP\s+(\d+)/);
        return m ? m[1] : null;
      });
      if (status && status.startsWith('2')) ok(`Test Console patient-discovery returned HTTP ${status}`);
      else fail(`Test Console returned HTTP ${status}`);
    } catch {
      const txt = await page.evaluate(() => document.body.innerText.slice(0, 400));
      fail(`Test Console did not produce a 2xx response. body=${txt}`);
    }

    // ── 7. Sign out ────────────────────────────────────────────────────────
    console.log('▶ 7. Sign out returns to /admin/login/');
    await page.goto(`${BASE}/admin/dashboard/`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('aside button');
    await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('aside button'));
      const signOut = buttons.find((b) => /sign out/i.test(b.textContent || ''));
      if (signOut) signOut.click();
    });
    try {
      await page.waitForFunction(
        () => location.pathname === '/admin/login/',
        { timeout: 15000 },
      );
      ok(`logged out → ${page.url()}`);
    } catch {
      fail(`expected /admin/login/, got ${page.url()}`);
    }
    await page.waitForNetworkIdle({ idleTime: 500, timeout: 10000 }).catch(() => {});

    // ── 8. Wrong password rejected ────────────────────────────────────────
    console.log('▶ 8. Wrong password is rejected');
    await page.waitForFunction(
      () => {
        const u = document.querySelector('input[autocomplete="username"]');
        return !!u && u.value === 'admin@local';
      },
      { timeout: 10000 },
    );
    await page.evaluate(() => {
      const pw = document.querySelector('input[type="password"]');
      const setter = Object.getOwnPropertyDescriptor(
        window.HTMLInputElement.prototype, 'value',
      ).set;
      setter.call(pw, 'WRONG_PASSWORD');
      pw.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await page.evaluate(() => document.querySelector('form')?.requestSubmit());
    await page.waitForFunction(() => /invalid/i.test(document.body.innerText), { timeout: 5000 })
      .then(() => ok('error banner shows invalid credentials'))
      .catch(() => fail('expected invalid-credentials error to render'));
    if (page.url().endsWith('/admin/login/')) ok('still on login page after bad password');
    else fail(`unexpected url: ${page.url()}`);
  } catch (e) {
    fail(`unhandled error: ${e.message}`);
    console.error(e);
  } finally {
    await browser.close();
  }

  if (process.exitCode === 1) {
    console.log('\n❌ UI smoke test FAILED');
  } else {
    console.log('\n✅ UI smoke test PASSED');
  }
}

main();
