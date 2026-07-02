import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for Mimope end-to-end browser tests.
 *
 * These tests exercise the real game flow in a headless browser against a
 * running backend and the built frontend. They cover the Phase 20 checklist
 * items that require actual browser sessions: multiplayer, death/respawn,
 * evolution, and leaderboard.
 *
 * The webServer blocks below start the stack automatically:
 *   - backend:  Spring Boot on :8080 with the `test` profile (enables the
 *               profile-gated TestSupportController used to make gameplay
 *               deterministic).
 *   - frontend: Vite preview on :4173 serving the production build, proxying
 *               /ws to the backend.
 *
 * Run with: npm run e2e
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'retain-on-failure',
    headless: true,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: [
    {
      command:
        '../backend/mvnw -q -f ../backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=test',
      url: 'http://localhost:8080/actuator/health',
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      // Build with an explicit backend WS URL so the browser connects straight
      // to the backend, bypassing the vite preview proxy (whose WebSocket
      // upgrade handling proved unreliable under test). Then serve that build.
      command:
        'VITE_WS_URL=ws://localhost:8080/ws/game npm run build && npm run preview -- --port 4173 --strictPort',
      url: 'http://localhost:4173',
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
});
