export const API_BASE_URL = "";

export const STATUS_LABELS = {
  DRAFT: "Draft",
  DOING: "Doing",
  REVIEW: "Review",
  DONE: "Done",
};

export const ACTIVE_STATUSES = new Set(["DRAFT", "DOING", "REVIEW"]);

export function malaysiaToday() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kuala_Lumpur" }).format(new Date());
}

export function timeGreeting(name) {
  const hour = new Date().getHours();
  const period = hour < 12 ? "morning" : hour < 18 ? "afternoon" : "evening";
  return `Good ${period}, ${name || "there"}`;
}

export function taskIsActive(task) {
  return ACTIVE_STATUSES.has(task.status);
}

export function taskNeedsAttention(task) {
  return taskIsActive(task) && task.dueDate && task.dueDate <= addDays(malaysiaToday(), 3);
}

export function addDays(dateValue, days) {
  const date = new Date(`${dateValue}T00:00:00+08:00`);
  date.setDate(date.getDate() + days);
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kuala_Lumpur" }).format(date);
}

export function dueLabel(task) {
  if (!task.dueDate || !taskIsActive(task)) return null;
  const today = malaysiaToday();
  if (task.dueDate < today) return "Overdue";
  if (task.dueDate === today) return "Due today";
  if (task.dueDate === addDays(today, 1)) return "Due tomorrow";
  if (task.dueDate <= addDays(today, 3)) return "Due soon";
  return null;
}

export function activeTasks(tasks) {
  return tasks.filter(taskIsActive);
}

export function activeWorkload(tasks) {
  return activeTasks(tasks).reduce((sum, task) => sum + (task.workload || 0), 0);
}

export function countStatus(tasks, status) {
  return tasks.filter((task) => task.status === status).length;
}

export async function getJson(path, apiFetch) {
  const response = await apiFetch(`${API_BASE_URL}${path}`);
  if (!response.ok) throw new Error(`Request failed (${response.status})`);
  return response.json();
}

export function formatDueDate(dateValue) {
  if (!dateValue) return "No due date";
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short", timeZone: "Asia/Kuala_Lumpur" })
    .format(new Date(`${dateValue}T00:00:00+08:00`));
}
