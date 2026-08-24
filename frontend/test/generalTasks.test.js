import test from "node:test";
import assert from "node:assert/strict";
import { activeWorkload, dueLabel, malaysiaToday, addDays } from "../src/pages/dashboardUtils.js";

test("active workload includes general tasks and excludes done work", () => {
  const tasks = [
    { generalTask: true, status: "DOING", workload: 4 },
    { generalTask: true, status: "DONE", workload: 5 },
    { generalTask: false, status: "DRAFT", workload: 2 },
  ];

  assert.equal(activeWorkload(tasks), 6);
});

test("active workload ignores tasks without an active workflow status", () => {
  assert.equal(activeWorkload([
    { status: "DRAFT", workload: 2 },
    { status: "DOING", workload: 3 },
    { status: "REVIEW", workload: 4 },
    { status: "DONE", workload: 20 },
    { status: null, workload: 50 },
  ]), 9);
});

test("general tasks use the existing deadline labels", () => {
  assert.equal(dueLabel({ generalTask: true, status: "DOING", dueDate: malaysiaToday() }), "Due today");
  assert.equal(dueLabel({ generalTask: true, status: "DOING", dueDate: addDays(malaysiaToday(), 1) }), "Due tomorrow");
  assert.equal(dueLabel({ generalTask: true, status: "DONE", dueDate: malaysiaToday() }), null);
});
