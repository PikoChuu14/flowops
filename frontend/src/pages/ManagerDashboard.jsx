import { useEffect, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import { activeTasks, activeWorkload, countStatus, dueLabel, formatDueDate, getJson, malaysiaToday, timeGreeting } from "./dashboardUtils";

function ManagerDashboard({ user, refreshKey, onViewKanban, onViewProject, onOpenKanban, onOpenReport, onOpenReviews }) {
  const [workload, setWorkload] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [myTasks, setMyTasks] = useState([]);
  const [mySnapshot, setMySnapshot] = useState(null);
  const [boards, setBoards] = useState([]);
  const [state, setState] = useState("loading");
  const [reportStatuses, setReportStatuses] = useState([]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setState("loading");
      try {
        const [team, ownTasks, dates] = await Promise.all([
          getJson("/api/dashboard/team-workload", apiFetch),
          getJson("/api/tasks/my", apiFetch),
          getJson("/api/history/dates", apiFetch).catch(() => []),
        ]);
        const reportResponse = await apiFetch(`/api/daily-reports/team/status?date=${malaysiaToday()}`);
        const reportData = reportResponse.ok ? await reportResponse.json() : [];
        const [staffTasks, teamBoards] = await Promise.all([
          Promise.all(team.map((staff) => getJson(`/api/tasks/user/${staff.userId}`, apiFetch))),
          getJson("/api/boards", apiFetch),
        ]);
        let startOfDay = null;
        const today = malaysiaToday();
        if (dates.some((date) => date.date === today && date.hasStartOfDay)) {
          startOfDay = await getJson(`/api/history/${today}/users/${user.userId}?type=START_OF_DAY`, apiFetch).catch(() => null);
        }
        if (!cancelled) { setWorkload(team); setTasks(staffTasks.flat()); setBoards(teamBoards); setMyTasks(ownTasks); setMySnapshot(startOfDay); setReportStatuses(reportData); setState("ready"); }
      } catch (error) { console.error(error); if (!cancelled) setState("error"); }
    }
    load();
    return () => { cancelled = true; };
  }, [refreshKey]);

  async function reviewAction(task, action) {
    const response = await apiFetch(`/api/tasks/${task.id}/review-action`, { method: "PUT", body: JSON.stringify({ action }) });
    if (!response.ok) return;
    setTasks((current) => current.map((candidate) => candidate.id === task.id ? { ...candidate, status: action === "APPROVE" ? "DONE" : "DOING" } : candidate));
    setWorkload((current) => current.map((staff) => {
      if (staff.userId !== task.assigneeId) return staff;
      const workloadDelta = task.workload || 0;
      return action === "APPROVE"
        ? { ...staff, totalWorkload: Math.max(0, staff.totalWorkload - workloadDelta), activeTaskCount: Math.max(0, staff.activeTaskCount - 1), reviewCount: Math.max(0, staff.reviewCount - 1), doneCount: staff.doneCount + 1 }
        : { ...staff, reviewCount: Math.max(0, staff.reviewCount - 1), doingCount: staff.doingCount + 1 };
    }));
  }

  if (state === "loading") return <section className="dashboard-page"><p className="dashboard-status">Loading your team dashboard...</p></section>;
  if (state === "error") return <section className="dashboard-page"><div className="dashboard-error">Unable to load dashboard.</div></section>;
  const active = activeTasks(tasks);
  const review = tasks.filter((task) => task.status === "REVIEW");
  const deadlineTasks = tasks.filter((task) => dueLabel(task));
  const overdue = deadlineTasks.filter((task) => dueLabel(task) === "Overdue");
  const dueSoon = deadlineTasks.filter((task) => dueLabel(task) !== "Overdue");
  const attention = [...new Map(tasks.filter((task) => task.status === "REVIEW" || dueLabel(task)).map((task) => [task.id, task])).values()]
    .sort((a, b) => attentionRank(a) - attentionRank(b))
    .slice(0, 8);
  const myActive = activeTasks(myTasks);
  const myAttention = myActive.filter((task) => task.status === "REVIEW" || dueLabel(task)).sort((a, b) => attentionRank(a) - attentionRank(b)).slice(0, 5);
  const myCurrent = myTasks.filter((task) => task.status === "DOING").concat(myTasks.filter((task) => task.status === "DRAFT")).slice(0, 5);
  const myTaskCount = myActive.length + review.length;

  return <section className="dashboard-page">
    <div className="dashboard-hero"><div><h1 className="personal-greeting">{timeGreeting(user.name)}</h1></div></div>
    <div className="manager-personal-heading"><div><p className="eyebrow">Your work</p><h2>My work today</h2></div></div>
    <div className="kpi-grid manager-personal-kpis"><Kpi label="My tasks" value={myTaskCount} detail="To Do, In Progress and Review" /><Kpi label="My doing" value={countStatus(myTasks, "DOING")} detail="In progress now" /><Kpi label="Team review" value={review.length} detail="Staff tasks awaiting action" /><Kpi label="My deadlines" value={myActive.filter((task) => dueLabel(task)).length} detail="Next 3 days" tone={myActive.some((task) => dueLabel(task)) ? "warning" : ""} /></div>
    <div className="dashboard-columns manager-personal-grid"><DashboardPanel title="My current work"><ManagerTaskList tasks={myCurrent} /><button type="button" className="primary-button dashboard-open-button" onClick={onOpenKanban}>Open My Kanban</button></DashboardPanel><DashboardPanel title="My needs attention">{myAttention.length ? <ManagerTaskList tasks={myAttention} showBadges /> : <Empty text="Nothing needs your attention right now." />}</DashboardPanel></div>
    <DashboardPanel title="My today's progress"><ManagerProgress snapshot={mySnapshot} current={myTasks} /></DashboardPanel>
    <div className="manager-personal-heading team-section-heading"><div><p className="eyebrow">Department view</p><h2>Team operations</h2></div></div>
    <div className="kpi-grid"><Kpi label="Staff count" value={workload.length} detail="In your department" /><Kpi label="Active tasks" value={active.length} detail="Not done" /><Kpi label="Active workload" value={activeWorkload(tasks)} detail="Across the team" /><Kpi label="Waiting for review" value={review.length} detail="Manager action" tone={review.length ? "warning" : ""} /><Kpi label="Due soon / overdue" value={deadlineTasks.length} detail={`${overdue.length} overdue · ${dueSoon.length} due soon`} tone={deadlineTasks.length ? "warning" : ""} /></div>
    <div className="dashboard-columns manager-grid">
      <DashboardPanel title="Daily Reports — Today"><div className="report-overview"><p><strong>{reportStatuses.filter(r=>r.status === "SUBMITTED").length}</strong> / {reportStatuses.length} submitted</p><button type="button" className="primary-button dashboard-open-button" onClick={() => onOpenReport()}>View Team Reports</button>{reportStatuses.map(r=><button className="report-status-row" key={r.userId} onClick={()=>onOpenReport()}><span>{r.userName}</span><span className={`report-status ${r.status.toLowerCase()}`}>{r.status === "NOT_STARTED" ? "Not started" : r.status === "DRAFT" ? "Draft" : "Submitted"}</span></button>)}</div>{!reportStatuses.length&&<Empty text="No staff reports for today."/>}</DashboardPanel>
      <DashboardPanel title="Needs my attention"><div className="attention-summary"><span><strong>{review.length}</strong> waiting for review</span><span><strong>{overdue.length}</strong> overdue</span><span><strong>{dueSoon.length}</strong> due soon</span></div>{review.length > 0 && <button type="button" className="primary-button dashboard-open-button" onClick={onOpenReviews}>View Reviews</button>}{attention.map((task) => <div className="dashboard-task attention-task" key={task.id}><div><strong>{task.title}</strong><small>{task.assigneeName || "Unassigned"} · {task.boardName || "No project"}{task.dueDate ? ` · ${task.dueDate}` : ""}</small></div><div className="attention-actions"><div className="attention-badges">{task.status === "REVIEW" && <span className="attention-badge review">Waiting for review</span>}{dueLabel(task) && <span className={`attention-badge ${dueLabel(task) === "Overdue" ? "overdue" : "deadline"}`}>{dueLabel(task)}</span>}</div>{task.status === "REVIEW" && <><button className="approve-button" onClick={() => reviewAction(task, "APPROVE")}>Approve</button><button className="return-button" onClick={() => reviewAction(task, "RETURN")}>Return</button></>}</div></div>)}{!attention.length && <Empty text="No tasks require your attention." />}</DashboardPanel>
      <DashboardPanel title="Team workload"><div className="team-list">{workload.map((staff) => <div className="team-row" key={staff.userId}><div><strong>{staff.name}</strong><small>{staff.activeTaskCount} active tasks · Doing {staff.doingCount} · Review {staff.reviewCount}</small></div><div className="team-workload"><strong>{staff.totalWorkload}</strong><span>workload</span><div className="mini-bar"><i style={{ width: `${Math.min(100, staff.totalWorkload * 8)}%` }} /></div></div><button className="text-button" onClick={() => onViewKanban(staff.userId)}>View Kanban</button></div>)}</div>{!workload.length && <Empty text="No staff found in this department." />}</DashboardPanel>
      <DashboardPanel title="Projects"><div className="project-list">{boards.map((board) => { const boardTasks = tasks.filter((task) => task.boardId === board.id); return <button className="project-row" key={board.id} onClick={() => onViewProject?.(board)}><span><strong>{board.name}</strong><small>{boardTasks.filter((task) => task.status !== "DONE").length} active · {countStatus(boardTasks, "REVIEW")} review</small></span><b>{boardTasks.filter((task) => dueLabel(task) === "Overdue").length || "—"}</b></button>; })}</div>{!boards.length && <Empty text="No active projects." />}</DashboardPanel>
    </div>
  </section>;
}
function Kpi({ label, value, detail, tone = "" }) { return <div className={`kpi-card ${tone}`}><span>{label}</span><strong>{value}</strong><small>{detail}</small></div>; }
function DashboardPanel({ title, children }) { return <section className="dashboard-panel"><div className="panel-heading"><h2>{title}</h2></div>{children}</section>; }
function Empty({ text }) { return <p className="dashboard-empty">{text}</p>; }
function ManagerTaskList({ tasks, showBadges = false }) { if (!tasks.length) return <Empty text="No active tasks." />; return <div className="dashboard-task-list">{tasks.map((task) => <div className="dashboard-task" key={task.id}><div><strong>{task.title}</strong><small>{task.boardName || "Personal work"} · Workload {task.workload ?? "—"}{task.dueDate ? ` · ${formatDueDate(task.dueDate)}` : ""}</small></div>{showBadges && <div className="attention-badges">{task.status === "REVIEW" && <span className="attention-badge review">Waiting for review</span>}{dueLabel(task) && <span className={`attention-badge ${dueLabel(task) === "Overdue" ? "overdue" : "deadline"}`}>{dueLabel(task)}</span>}</div>}</div>)}</div>; }
function ManagerProgress({ snapshot, current }) { if (!snapshot) return <Empty text="Start-of-day snapshot not available." />; return <div className="progress-comparison">{["DRAFT", "DOING", "REVIEW", "DONE"].map((status) => <div className="progress-row" key={status}><strong>{status[0] + status.slice(1).toLowerCase()}</strong><b>{countStatus(snapshot, status)}</b><span>→</span><b>{countStatus(current, status)}</b></div>)}</div>; }
function attentionRank(task) { if (dueLabel(task) === "Overdue") return 1; if (dueLabel(task) === "Due today") return 2; if (dueLabel(task) === "Due tomorrow") return 3; if (dueLabel(task) === "Due soon") return 4; return 5; }
export default ManagerDashboard;
