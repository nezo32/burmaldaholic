import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['assets/**/*.test.mjs'],
    environment: 'node',
  },
});
