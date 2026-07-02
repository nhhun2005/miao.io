import { Page, expect } from '@playwright/test';

/**
 * Enter a nickname on the home screen and click Play, then wait until the
 * game screen is active (the HUD nickname is visible).
 */
export async function joinGame(page: Page, nickname: string): Promise<void> {
  await page.goto('/');
  await page.getByPlaceholder('Enter your name…').fill(nickname);
  await page.getByRole('button', { name: 'Play' }).click();
  // The HUD shows the nickname once we reach the game screen.
  await expect(page.locator('.game-hud__nickname')).toHaveText(nickname, {
    timeout: 20_000,
  });
}

/** Call a profile-gated test-support endpoint on the backend. */
export async function testSupport(
  page: Page,
  path: string,
  params: Record<string, string | number>,
): Promise<void> {
  const query = new URLSearchParams(
    Object.entries(params).map(([k, v]) => [k, String(v)]),
  ).toString();
  const res = await page.request.post(
    `http://localhost:8080/test-support/${path}?${query}`,
  );
  expect(res.ok(), `test-support/${path} should succeed`).toBeTruthy();
}
