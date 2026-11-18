// SPDX-License-Identifier: Apache-2.0
import { test as base, expect } from '@playwright/test';

// Candidate screenshot tests use setContent() and actual bundled UI with injected
// fixtures. Every browser network request is refused, including unexpected assets.
export const test = base.extend({
  context: async ({ context }, use) => {
    await context.route('**/*', route => route.abort('blockedbyclient'));
    await use(context);
  },
});
export { expect };
