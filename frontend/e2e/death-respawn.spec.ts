import { test, expect } from '@playwright/test';
import { joinGame, testSupport } from './helpers';

test.describe('Death and respawn', () => {
  test('being killed shows the death screen, then Play Again returns to the join flow', async ({
    page,
  }) => {
    const logs: string[] = [];
    page.on('console', (m) => logs.push(`[console] ${m.text()}`));
    page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`));

    await joinGame(page, 'DeathE2E');

    // Force a server-side kill; the victim receives a death message.
    await testSupport(page, 'kill', { nickname: 'DeathE2E' });

    // Death screen appears.
    try {
      await expect(page.locator('.death-panel__title')).toHaveText('You Died!', {
        timeout: 20_000,
      });
    } catch (err) {
      console.log('--- captured browser logs ---');
      console.log(logs.join('\n'));
      throw err;
    }

    // Play Again returns through the normal join flow (home screen).
    await page.getByRole('button', { name: 'Play Again' }).click();
    await expect(page.getByPlaceholder('Enter your name…')).toBeVisible({
      timeout: 20_000,
    });

    // Rejoin works cleanly.
    await joinGame(page, 'DeathE2E');
    await expect(page.locator('.game-hud__nickname')).toHaveText('DeathE2E');
  });
});
