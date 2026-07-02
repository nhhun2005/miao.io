import { test, expect } from '@playwright/test';
import { joinGame, testSupport } from './helpers';

test.describe('Evolution', () => {
  test('reaching the XP threshold shows the evolution modal and applies the choice', async ({
    page,
  }) => {
    await joinGame(page, 'EvoE2E');

    // Mouse -> Rabbit requires 50 XP. Grant enough to trigger options.
    await testSupport(page, 'grant-xp', { nickname: 'EvoE2E', amount: 60 });

    // The evolution modal appears with at least one option.
    const modal = page.locator('.evolution-modal');
    await expect(modal).toBeVisible({ timeout: 20_000 });

    const rabbit = page.locator('.evolution-card', { hasText: 'Rabbit' });
    await expect(rabbit).toBeVisible();
    await rabbit.click();

    // After evolving, the HUD animal label updates to Rabbit and the modal closes.
    await expect(page.locator('.game-hud__animal')).toHaveText('Rabbit', {
      timeout: 20_000,
    });
    await expect(modal).toHaveCount(0);
  });
});
