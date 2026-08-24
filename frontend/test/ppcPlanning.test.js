import test from "node:test";
import assert from "node:assert/strict";
import { addDays, eventDatesToPlanningDates, itemsForDate, planningItemToEvent } from "../src/pages/ppcPlanningUtils.js";

test("FullCalendar events use Monday-first-compatible ISO dates and inclusive end conversion", () => {
  const event = planningItemToEvent({ id: 4, title: "Trial", startDate: "2026-08-10", endDate: "2026-08-12" });
  assert.deepEqual(event, { id: "4", title: "Trial", start: "2026-08-10", end: "2026-08-13", allDay: true, extendedProps: { item: { id: 4, title: "Trial", startDate: "2026-08-10", endDate: "2026-08-12" } } });
});

test("FullCalendar exclusive end dates convert back to inclusive FlowOps dates", () => {
  assert.deepEqual(eventDatesToPlanningDates({ startStr: "2026-08-10", endStr: "2026-08-13" }), { startDate: "2026-08-10", endDate: "2026-08-12" });
  assert.equal(addDays("2026-12-31", 1), "2027-01-01");
});

test("multi-day planning items intersect each affected date", () => {
  const item = { startDate: "2026-07-31", endDate: "2026-08-02" };
  assert.equal(itemsForDate([item], "2026-08-01").length, 1);
  assert.equal(itemsForDate([item], "2026-08-03").length, 0);
});
