import test from "node:test";
import assert from "node:assert/strict";
import { activeMonthlyWorkload, projectLabel, shiftMonth } from "../src/pages/monthlyReportUtils.js";

test("monthly navigation crosses calendar year boundaries", () => {
  assert.deepEqual(shiftMonth(2026, 1, -1), { year: 2025, month: 12 });
  assert.deepEqual(shiftMonth(2026, 12, 1), { year: 2027, month: 1 });
});

test("general tasks receive a safe display label", () => {
  assert.equal(projectLabel({ projectName: null }), "General Tasks");
  assert.equal(projectLabel({ boardName: "Prototype" }), "Prototype");
});

test("monthly active workload excludes completed tasks", () => {
  assert.equal(activeMonthlyWorkload([{ status: "DOING", workload: 4 }, { status: "DONE", workload: 8 }, { status: "DRAFT", workload: 2 }]), 6);
});
