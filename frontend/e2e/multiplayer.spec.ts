import { test, expect } from '@playwright/test';
import { joinGame, testSupport } from './helpers';

test.describe('Multiplayer', () => {
  test('two browser sessions join the same room and see each other on the leaderboard', async ({
    browser,
  }) => {
    const ctxA = await browser.newContext();
    const ctxB = await browser.newContext();
    const pageA = await ctxA.newPage();
    const pageB = await ctxB.newPage();

    await joinGame(pageA, 'AliceE2E');
    await joinGame(pageB, 'BobE2E');

    // Grant XP so both appear on the leaderboard deterministically.
    await testSupport(pageA, 'grant-xp', { nickname: 'AliceE2E', amount: 30 });
    await testSupport(pageA, 'grant-xp', { nickname: 'BobE2E', amount: 10 });

    // Both players should show up in each other's leaderboard panel.
    await expect(pageA.locator('.leaderboard-panel')).toContainText('AliceE2E');
    await expect(pageA.locator('.leaderboard-panel')).toContainText('BobE2E');
    await expect(pageB.locator('.leaderboard-panel')).toContainText('AliceE2E');
    await expect(pageB.locator('.leaderboard-panel')).toContainText('BobE2E');

    await ctxA.close();
    await ctxB.close();
  });
});
