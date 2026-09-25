import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['assets/**/*.test.mjs'],
    environment: 'node',
    // The determinism tests re-run a whole art module (the card atlas takes ~2 s idle, 8+ s on a loaded build
    // machine next to Gradle); vitest's 5 s default made them fail on load alone, not on a wrong output.
    testTimeout: 60_000,
  },
});
