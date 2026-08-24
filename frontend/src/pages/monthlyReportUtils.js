export function shiftMonth(year, month, delta) {
  const date = new Date(year, month - 1 + delta, 1);
  return { year: date.getFullYear(), month: date.getMonth() + 1 };
}

export function projectLabel(task) {
  return task?.projectName || task?.boardName || "General Tasks";
}

export function activeMonthlyWorkload(tasks = []) {
  return tasks.filter((task) => task?.status !== "DONE").reduce((total, task) => total + (Number(task?.workload) || 0), 0);
}
