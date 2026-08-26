import test from "node:test";
import assert from "node:assert/strict";
import { getNavigationItems } from "../src/components/navigationModel.js";

test("PPC staff navigation follows operational priority order", () => {
  assert.deepEqual(getNavigationItems({ role: "STAFF", departmentName: "PPC" }).map((item) => item[0]), ["dashboard", "personal", "project", "ppc-arrivals", "ppc-planning", "requests", "completed", "report", "desktop-notifications"]);
});
test("PPC manager navigation puts team and reviews after project work", () => {
  assert.deepEqual(getNavigationItems({ role: "MANAGER", departmentName: "PPC" }).map((item) => item[0]), ["dashboard", "personal", "project", "ppc-arrivals", "ppc-planning", "requests", "completed", "staff", "reviews", "report", "desktop-notifications"]);
});
test("non-PPC staff does not see PPC modules", () => {
  assert.equal(getNavigationItems({ role: "STAFF", departmentName: "RDD" }).some((item) => item[0].startsWith("ppc-")), false);
});
