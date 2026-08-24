import { useEffect, useState } from "react";
import { apiFetch } from "../api/apiFetch";

const API_BASE_URL = "";
const STATUSES = [
  { value: "DRAFT", label: "Draft" },
  { value: "DOING", label: "Doing" },
  { value: "REVIEW", label: "Review" },
  { value: "DONE", label: "Done" },
];

function StaffKanban({ staffUser, refreshKey, onTaskSelected, onTaskChanged, onReassignTask }) {
  const [tasks, setTasks] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!staffUser) {
      return;
    }

    async function loadStaffTasks() {
      try {
        setError("");
        const response = await apiFetch(
          `${API_BASE_URL}/api/tasks/user/${staffUser.id}`
        );
        if (!response.ok) {
          throw new Error(`Failed to load staff tasks (${response.status})`);
        }
        setTasks(await response.json());
      } catch (loadError) {
        console.error("Failed to load staff Kanban:", loadError);
        setError("Unable to load this staff member's Kanban.");
      }
    }

    loadStaffTasks();
  }, [staffUser, refreshKey]);

  async function handleReviewAction(task, action) {
    try {
      const response = await apiFetch(
        `${API_BASE_URL}/api/tasks/${task.id}/review-action`,
        {
          method: "PUT",
          body: JSON.stringify({ action }),
        }
      );

      if (!response.ok) {
        throw new Error(`Review action failed (${response.status})`);
      }

      const updatedTask = await response.json();
      setTasks((currentTasks) => currentTasks.map((candidate) =>
        candidate.id === updatedTask.id ? updatedTask : candidate
      ));
      onTaskChanged?.();
    } catch (actionError) {
      console.error("Failed to apply review action:", actionError);
      setError("Unable to update the task review state.");
    }
  }

  return (
    <>
      <h1>{staffUser ? `${staffUser.name}'s Kanban` : "Staff Kanban"}</h1>

      {error && <p className="form-error" role="alert">{error}</p>}

      {!staffUser ? (
        <div className="empty-state">
          <p>Select a staff member to view their work.</p>
        </div>
      ) : (
        <div className="kanban-board personal-kanban staff-kanban">
          {STATUSES.map((status) => (
            <div className="kanban-column" key={status.value}>
              <div className="column-header">
                <h2>{status.label}</h2>
              </div>

              <div className="task-list">
                {tasks
                  .filter((task) => task.status === status.value)
                  .map((task) => (
                    <div
                      className="task-card staff-task-card"
                      key={task.id}
                      onClick={() => onTaskSelected(task)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          onTaskSelected(task);
                        }
                      }}
                    >
                      <h3>{task.title}</h3>
                      <small className="task-board-name">{task.generalTask ? "GENERAL · PPC" : task.boardName}</small>
                      <p>{task.description}</p>
                      <div className="task-meta">
                        <span>{task.priority} · Workload {task.workload ?? "—"}</span>
                      </div>
                      {task.dueDate && <small>Due: {task.dueDate}</small>}

                      {status.value === "REVIEW" && (
                        <div className="review-actions" onClick={(event) => event.stopPropagation()}>
                          <button
                            type="button"
                            className="review-return-button"
                            onClick={() => handleReviewAction(task, "RETURN")}
                          >
                            Return for Changes
                          </button>
                          <button
                            type="button"
                            className="review-approve-button"
                            onClick={() => handleReviewAction(task, "APPROVE")}
                          >
                            Approve
                          </button>
                        </div>
                      )}
                      <button
                        type="button"
                        className="reassign-button"
                        onClick={(event) => {
                          event.stopPropagation();
                          onReassignTask(task);
                        }}
                      >
                        Reassign
                      </button>
                    </div>
                  ))}
                {tasks.filter((task) => task.status === status.value).length === 0 && (
                  <p className="column-empty">No tasks assigned.</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

export default StaffKanban;
