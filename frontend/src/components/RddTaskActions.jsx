import { useState } from "react";
import { apiFetch } from "../api/apiFetch";

export default function RddTaskActions({ task, user, onChanged }) {
  const [busy, setBusy] = useState(false);
  const isRdd = task?.departmentName?.toUpperCase() === "RDD";
  const isManager = user?.role === "MANAGER" || user?.role === "ADMIN";
  const isAssignee = task?.assigneeId != null && task.assigneeId === user?.userId;
  if (!isRdd) return null;
  const action = task.status === "DRAFT" && isAssignee ? { label: "Start Work", path: "status", body: { status: "DOING", targetPosition: 1 } } : null;
  const canSendToReview = task.status === "DOING" && isAssignee;
  const canSendToTodo = task.status === "DOING" && isAssignee;
  const canReturn = task.status === "REVIEW" && (isManager || isAssignee);
  const canApprove = task.status === "REVIEW" && isManager;
  if (!action && !canSendToReview && !canSendToTodo && !canReturn && !canApprove) return null;
  async function submit(path, body) {
    setBusy(true);
    try { const response = await apiFetch(`/api/tasks/${task.id}/${path}`, { method: "PUT", body: JSON.stringify(body) }); if (!response.ok) throw new Error(); await onChanged?.(await response.json()); }
    catch { /* Parent refresh/error handling remains authoritative. */ }
    finally { setBusy(false); }
  }
  return <div className="rdd-task-actions" onPointerDown={(event) => event.stopPropagation()} onClick={(event) => event.stopPropagation()}>{action && <button type="button" className="primary-button" disabled={busy} onClick={() => void submit(action.path, action.body)}>{busy ? "Updating…" : action.label}</button>}{canSendToTodo && <button type="button" className="secondary-button" disabled={busy} onClick={() => void submit("status", { status: "DRAFT", targetPosition: 1 })}>Send to To Do</button>}{canSendToReview && <button type="button" className="primary-button" disabled={busy} onClick={() => void submit("status", { status: "REVIEW", targetPosition: 1 })}>{busy ? "Updating…" : "Send to Review"}</button>}{canReturn && <button type="button" className="secondary-button" disabled={busy} onClick={() => void submit("review-action", { action: "RETURN" })}>Return to Work</button>}{canApprove && <button type="button" className="primary-button" disabled={busy} onClick={() => void submit("review-action", { action: "APPROVE" })}>{busy ? "Updating…" : "Approve"}</button>}</div>;
}
