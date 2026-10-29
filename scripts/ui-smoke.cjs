const assert = require('node:assert/strict');
const fs = require('node:fs');
const { chromium } = require('playwright');

const base = process.env.APP_URL || 'http://127.0.0.1:8080';
const mailbox = process.env.MAILPIT_URL || 'http://127.0.0.1:8025';
assert(['127.0.0.1', 'localhost'].includes(new URL(base).hostname), 'Helyi tesztkiszolgálót használj.');
const entity = '/odata/v4/spacefarer/Spacefarers';

async function until(check) {
    for (let attempt = 0; attempt < 80; attempt++) {
        const result = await check();
        if (result) return result;
        await new Promise(resolve => setTimeout(resolve, 250));
    }
    throw new Error('A mentés ellenőrzése túllépte az időkorlátot.');
}

async function main() {
    fs.mkdirSync('.local', { recursive: true });
    const browser = await chromium.launch({ headless: true, channel: process.env.BROWSER_CHANNEL || undefined });
    const context = await browser.newContext({
        baseURL: base, locale: 'hu-HU', httpCredentials: { username: 'pilot-x', password: 'local-x' },
        viewport: { width: 1440, height: 1000 }
    });
    const page = await context.newPage();
    page.setDefaultTimeout(30000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const stamp = Date.now();
    const name = `UI Pilot ${stamp}`;
    const email = `ui.${stamp}@example.test`;
    let createdId;
    const field = key => page.locator(`input[id*="FormElement::DataField::${key}::"]`);
    const records = async () => {
        const response = await context.request.get(`${entity}?$filter=${encodeURIComponent(`fullName eq '${name}' and IsActiveEntity eq true`)}`);
        assert.equal(response.status(), 200);
        return (await response.json()).value;
    };
    const messages = async () => {
        const response = await fetch(`${mailbox}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
        assert.equal(response.status, 200);
        return (await response.json()).messages;
    };

    try {
        await page.goto('/spacefarers/index.html');
        await page.getByRole('button', { name: 'Go', exact: true }).click();
        await page.getByText('Nova Vega', { exact: true }).waitFor();
        assert.equal(await page.getByText('Luna Sol', { exact: true }).count(), 0);
        await page.screenshot({ path: '.local/list.png' });

        await page.getByRole('button', { name: 'Create', exact: true }).click();
        await until(async () => (await field('originPlanet_code').inputValue()) === 'Planet X');
        await field('fullName').fill(name);
        await field('fullName').press('Tab');
        await field('email').fill(email);
        await field('spacesuitColor').fill('Blue');
        await field('stardustCollection').fill('50');
        await field('wormholeNavigationSkill').fill('80');
        await field('wormholeNavigationSkill').press('Tab');
        assert.equal((await messages()).length, 0);
        await page.getByRole('button', { name: /^(Create|Save)$/ }).click();
        await page.getByRole('button', { name: 'Edit', exact: true }).waitFor();
        const created = await until(async () => (await records())[0]);
        createdId = created.ID;
        assert.equal(created.navigationRank, 'Expert');
        assert.equal(created.originPlanet_code, 'X');
        const received = await until(async () => (await messages())[0]);
        const mail = await (await fetch(`${mailbox}/api/v1/message/${received.ID}`)).json();
        assert.match(mail.Text, /Congratulations/);
        assert.match(mail.Text, new RegExp(name));
        await page.screenshot({ path: '.local/detail.png' });

        await page.getByRole('button', { name: 'Edit', exact: true }).click();
        await field('spacesuitColor').fill('Green');
        await field('wormholeNavigationSkill').fill('20');
        await field('wormholeNavigationSkill').press('Tab');
        await page.getByRole('button', { name: 'Save', exact: true }).click();
        await page.getByRole('button', { name: 'Edit', exact: true }).waitFor();
        await until(async () => (await records())[0]?.spacesuitColor === 'Green');
        assert.equal((await records())[0].navigationRank, 'Cadet');
        assert.equal((await messages()).length, 1);

        await page.goto('/spacefarers/index.html');
        const nameFilter = page.locator('input[id*="FilterField::fullName-inner-inner"]');
        await nameFilter.fill(name);
        await nameFilter.press('Enter');
        await page.getByRole('button', { name: 'Go', exact: true }).click();
        await page.getByText(name, { exact: true }).waitFor();
        assert.equal(await page.getByText('Nova Vega', { exact: true }).count(), 0);
        await page.getByText(name, { exact: true }).click();
        await page.getByRole('heading', { name, exact: true }).waitFor();
        await page.getByRole('button', { name: 'Delete', exact: true }).click();
        await page.locator('[role="dialog"], [role="alertdialog"]').getByRole('button', { name: 'Delete', exact: true }).click();
        await until(async () => (await records()).length === 0);
        createdId = undefined;

        const mobile = await browser.newContext({
            baseURL: base, locale: 'hu-HU', viewport: { width: 390, height: 844 },
            httpCredentials: { username: 'pilot-y', password: 'local-y' }
        });
        const phone = await mobile.newPage();
        await phone.goto('/spacefarers/index.html');
        await phone.getByRole('button', { name: 'Go', exact: true }).click();
        await phone.getByText('Luna Sol', { exact: true }).waitFor();
        assert.equal(await phone.getByText('Nova Vega', { exact: true }).count(), 0);
        await phone.screenshot({ path: '.local/mobile.png' });
        await mobile.close();
        assert.deepEqual(errors, []);
        console.log('rendben: lista, létrehozás, SMTP-levél, szerkesztés, szűrés, törlés, mobilnézet és bolygónkénti hozzáférés');
    } catch (error) {
        await page.screenshot({ path: '.local/failure.png', fullPage: true });
        throw error;
    } finally {
        if (createdId) {
            await context.request.delete(`${entity}(ID=${createdId},IsActiveEntity=true)`);
        }
        await browser.close();
    }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
