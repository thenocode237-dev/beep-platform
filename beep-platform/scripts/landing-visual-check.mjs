// Optional visual review: requires Playwright and a running local Next.js site.
// LANDING_BASE_URL, PLAYWRIGHT_MODULE and CHROME_EXECUTABLE can override local defaults.
import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE
  ? pathToFileURL(resolve(process.env.PLAYWRIGHT_MODULE)).href : "playwright");
const base = process.env.LANDING_BASE_URL || "http://localhost:3307";
const output = resolve(process.env.LANDING_REVIEW_DIR || "docs/previews/landing");
await mkdir(output, { recursive: true });
const browser = await chromium.launch({
  headless: true,
  ...(process.env.CHROME_EXECUTABLE ? { executablePath: process.env.CHROME_EXECUTABLE } : {}),
  args: ["--disable-background-networking", "--disable-component-update", "--no-default-browser-check"],
});
const results = [];
let checks = 0;
const check = (condition, message) => { checks++; assert.ok(condition, message); };

async function open(locale, width, options = {}) {
  const context = await browser.newContext({
    viewport: { width, height: width === 320 ? 568 : width < 768 ? 844 : 1000 },
    deviceScaleFactor: 1,
    isMobile: width < 768,
    hasTouch: width < 768,
    colorScheme: "light",
    reducedMotion: "no-preference",
    ...options,
  });
  const errors = [];
  const page = await context.newPage();
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error" && /hydration|uncaught|react error/i.test(message.text())) errors.push(message.text());
  });
  const response = await page.goto(base + (locale === "en" ? "/en" : "/"), { waitUntil: "load", timeout: 180_000 });
  check(response?.status() === 200, `${locale}/${width}: HTTP 200`);
  await page.evaluate(() => document.fonts.ready.then(() => true));
  if (options.javaScriptEnabled !== false) await page.waitForSelector(".landing-page[data-motion]", { timeout: 60_000 });
  return { context, page, errors };
}

async function scrollTo(page, selector) {
  await page.locator(selector).first().evaluate((element) => {
    const top = element.getBoundingClientRect().top + window.scrollY - 110;
    window.scrollTo({ top, behavior: "instant" });
  });
  await page.waitForTimeout(550);
}

async function prepareScreenshot(page) {
  await page.evaluate(async () => {
    for (let top = 0; top < document.documentElement.scrollHeight; top += window.innerHeight * .8) {
      window.scrollTo({ top, behavior: "instant" });
      await new Promise((resolve) => setTimeout(resolve, 90));
    }
    window.scrollTo({ top: 0, behavior: "instant" });
  });
  await page.waitForFunction(() => [...document.images].every((image) => image.complete && image.naturalWidth > 0), { timeout: 30_000 });
  await page.waitForTimeout(550);
}

try {
  for (const locale of ["fr", "en"]) {
    for (const width of [320, 375, 390, 768, 1024, 1440]) {
      const start = checks;
      const { context, page, errors } = await open(locale, width);
      try {
        check(await page.locator("h1").count() === 1, `${locale}/${width}: one main heading`);
        check(await page.locator("html").getAttribute("lang") === locale, `${locale}/${width}: document language`);
        const content = await page.locator("main").textContent();
        check(!/bêta privée|à venir|futurs outils|private beta|upcoming product|come next|arrivent ensuite/i.test(content), `${locale}/${width}: current product information`);
        check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${locale}/${width}: no horizontal overflow`);
        const cta = page.locator("[data-hero-cta] .bp-btn");
        const box = await cta.boundingBox();
        check(box && box.height >= 44 && box.width >= 44, `${locale}/${width}: comfortable primary touch target`);
        check(await cta.getAttribute("href") === (locale === "fr" ? "/signup" : "/en/signup"), `${locale}/${width}: localized signup`);
        check(await page.locator(`.growth-section a[href="${locale === "fr" ? "/docs" : "/en/docs"}"]`).count() === 1, `${locale}/${width}: connected API documentation`);
        const scene = await page.locator(".hero .product-scene").boundingBox();
        const copy = await page.locator(".hero__copy").boundingBox();
        check(scene && copy && (width < 768 ? scene.y < copy.y : width < 1024 ? copy.y < scene.y : copy.x < scene.x), `${locale}/${width}: illustration first on mobile, text first on larger screens`);

        if (width === 390) {
          await page.waitForTimeout(450);
          const heroInView = await page.locator("[data-hero-cta]").evaluate((element) => {
            const bounds = element.getBoundingClientRect();
            return bounds.bottom > 80 && bounds.top < innerHeight - 92;
          });
          check(await page.locator(".sticky-cta").getAttribute("aria-hidden") === String(heroInView), `${locale}: fixed CTA adapts to the visible primary action`);
          if (locale === "fr") await page.screenshot({ path: resolve(output, "mobile-first.png"), animations: "disabled" });
          await scrollTo(page, "#possibilities");
          await page.waitForFunction(() => document.querySelector(".sticky-cta")?.getAttribute("data-visible") === "true");
          check(await page.locator(".sticky-cta").isVisible(), `${locale}: CTA available after scrolling`);
          check(await page.locator(".sticky-cta").getAttribute("inert") === null, `${locale}: visible CTA is focusable`);
          await scrollTo(page, "[data-final-cta]");
          await page.waitForFunction(() => document.querySelector(".sticky-cta")?.getAttribute("data-visible") === "false");
          check(await page.locator(".sticky-cta").getAttribute("inert") !== null, `${locale}: final CTA replaces the fixed control`);
        }

        await page.locator("#faq summary").first().click();
        check(await page.locator("#faq details").first().getAttribute("open") !== null, `${locale}/${width}: FAQ opens by touch`);
        await page.locator("#faq summary").first().press("Enter");
        check(await page.locator("#faq details").first().getAttribute("open") === null, `${locale}/${width}: FAQ works with keyboard`);

        if (width === 390 || (width === 1440 && locale === "fr")) {
          await prepareScreenshot(page);
          await page.screenshot({ path: resolve(output, `${locale}-${width}.png`), fullPage: true, animations: "disabled" });
        }
        check(errors.length === 0, `${locale}/${width}: no browser errors: ${errors.join("; ")}`);
        results.push({ locale, width, mode: "standard", checks: checks - start });
      } finally { await context.close(); }
    }
  }

  for (const mode of ["reduced-motion", "no-javascript", "dark"]) {
    const start = checks;
    const { context, page, errors } = await open("fr", 390, {
      ...(mode === "reduced-motion" ? { reducedMotion: "reduce" } : {}),
      ...(mode === "no-javascript" ? { javaScriptEnabled: false } : {}),
      ...(mode === "dark" ? { colorScheme: "dark" } : {}),
    });
    try {
      check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${mode}: no horizontal overflow`);
      if (mode === "reduced-motion") {
        await scrollTo(page, "#possibilities");
        check(await page.locator(".landing-page").getAttribute("data-motion") === "reduced", "reduced motion preference honored");
        check(await page.evaluate(() => document.getAnimations().filter((animation) => animation.playState === "running").length === 0), "no running motion when reduction is requested");
      }
      if (mode === "no-javascript") {
        check(await page.locator(".feature").first().isVisible(), "content visible without JavaScript");
        await page.locator("#faq summary").first().click();
        check(await page.locator("#faq details").first().getAttribute("open") !== null, "FAQ remains usable without JavaScript");
      }
      if (mode === "dark") {
        await prepareScreenshot(page);
        await page.screenshot({ path: resolve(output, "fr-dark-390.png"), fullPage: true, animations: "disabled" });
      }
      check(errors.length === 0, `${mode}: no browser errors`);
      results.push({ locale: "fr", width: 390, mode, checks: checks - start });
    } finally { await context.close(); }
  }
  await writeFile(resolve(output, "report.json"), JSON.stringify({ checks, results }, null, 2) + "\n");
  console.log(`${checks} contrôles réussis sur ${results.length} configurations. Aperçus : ${output}`);
} finally { await browser.close(); }
