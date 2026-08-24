import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const page = readFileSync(new URL("../src/pages/DesktopNotificationsPage.jsx", import.meta.url), "utf8");
const app = readFileSync(new URL("../src/App.jsx", import.meta.url), "utf8");

test("desktop notification settings uses one-time code and device lifecycle APIs", () => {
  assert.match(page, /\/api\/devices\/register-code/);
  assert.match(page, /\/api\/devices\/revoke\/\$\{id\}/);
  assert.match(page, /Codes expire after 10 minutes and work once/);
  assert.match(app, /settings\/desktop-notifications/);
});
