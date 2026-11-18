// SPDX-License-Identifier: Apache-2.0
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '../tests/ci',
  testMatch: 'screenshots.spec.ts',
  outputDir: '../.ci-results/screenshots',
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 30_000,
  reporter: [['junit', { outputFile: '.ci-results/screenshots.xml' }]],
  use: {
    browserName: 'chromium',
    headless: true,
    serviceWorkers: 'block',
    viewport: { width: 1280, height: 720 },
    locale: 'en-US',
    timezoneId: 'UTC',
    colorScheme: 'dark',
    launchOptions: { args: ['--disable-gpu', '--disable-dev-shm-usage'] },
    trace: 'off',
    video: 'off',
  },
});
