import { useEffect, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import { activeTasks, activeWorkload, countStatus, dueLabel, getJson, timeGreeting } from "./dashboardUtils";

const WORKLOAD_COLORS = ["#3566b3", "#5f8fcf", "#6d829e", "#8b9bb0"];

function AdminDashboard({ user, refreshKey, departments, onViewDepartment, onViewProject }) {
  const [users, setUsers] = useState([]);
  const [boards, setBoards] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [history, setHistory] = useState(null);
  const [state, setState] = useState("loading");
  useEffect(() => { let cancelled = false; async function load() { setState("loading"); try {
    const [allUsers, allBoards, dates] = await Promise.all([getJson("/api/users/assignable", apiFetch), getJson("/api/boards", apiFetch), getJson("/api/history/dates", apiFetch).catch(() => [])]);
    const departmentTasks = await Promise.all(departments.map((department) => getJson(`/api/tasks/department/${department.id}`, apiFetch)));
    let latest = null; const endDate = dates.filter((date) => date.hasEndOfDay).sort((a, b) => b.date.localeCompare(a.date))[0];
    if (endDate) latest = await getJson(`/api/history/${endDate.date}?type=END_OF_DAY`, apiFetch).catch(() => null);
    if (!cancelled) { setUsers(allUsers); setBoards(allBoards); setTasks(departmentTasks.flat()); setHistory(latest); setState("ready"); }
  } catch (error) { console.error(error); if (!cancelled) setState("error"); } } load(); return () => { cancelled = true; }; }, [departments, refreshKey]);
  if (state === "loading") return <section className="dashboard-page"><p className="dashboard-status">Loading company dashboard...</p></section>;
  if (state === "error") return <section className="dashboard-page"><div className="dashboard-error">Unable to load dashboard.</div></section>;
  const active = activeTasks(tasks); const review = tasks.filter((task) => task.status === "REVIEW"); const overdue = tasks.filter((task) => dueLabel(task) === "Overdue");
  return <section className="dashboard-page"><div className="dashboard-hero"><div><h1 className="personal-greeting">{timeGreeting(user?.name)}</h1><p className="greeting-subtitle">Here's what's happening across the company today.</p></div></div>
    <div className="kpi-grid"><Kpi label="Departments" value={departments.length} detail="Company-wide" /><Kpi label="Employees" value={users.length} detail="All roles" /><Kpi label="Active tasks" value={active.length} detail="Not done" /><Kpi label="Active workload" value={activeWorkload(tasks)} detail="Across departments" /><Kpi label="Waiting for review" value={review.length} detail="Operational queue" tone={review.length ? "warning" : ""} /><Kpi label="Overdue tasks" value={overdue.length} detail="Active tasks only" tone={overdue.length ? "warning" : ""} /></div>
    <div className="dashboard-columns admin-grid"><DashboardPanel title="Department overview"><div className="department-list">{departments.map((department) => { const departmentTasks = tasks.filter((task) => task.departmentId === department.id); const staffCount = users.filter((user) => user.departmentId === department.id && user.role === "STAFF").length; return <button className="department-row" key={department.id} onClick={() => onViewDepartment(department.id)}><span><strong>{department.name}</strong><small>{staffCount} staff · {departmentTasks.filter((task) => task.status !== "DONE").length} active tasks</small></span><span><b>{activeWorkload(departmentTasks)}</b> workload</span><span>{countStatus(departmentTasks, "REVIEW")} review · {departmentTasks.filter((task) => dueLabel(task) === "Overdue").length} overdue</span></button>; })}</div>{!departments.length && <Empty text="No departments available." />}</DashboardPanel>
      <DashboardPanel title="Company workload"><CompanyWorkloadChart departments={departments} tasks={tasks} /></DashboardPanel>
      <DashboardPanel title="Project overview"><div className="project-list">{boards.map((board) => { const boardTasks = tasks.filter((task) => task.boardId === board.id); return <button className="project-row" key={board.id} onClick={() => onViewProject(board)}><span><strong>{board.name}</strong><small>{board.departmentName} · {boardTasks.filter((task) => task.status !== "DONE").length} active</small></span><b>{countStatus(boardTasks, "REVIEW")} review</b></button>; })}</div>{!boards.length && <Empty text="No active projects." />}</DashboardPanel>
      <DashboardPanel title="Latest end-of-day snapshot">{history ? <div className="snapshot-summary">{["DRAFT", "DOING", "REVIEW", "DONE"].map((status) => <div key={status}><span>{status[0] + status.slice(1).toLowerCase()}</span><strong>{countStatus(history, status)}</strong></div>)}<small>Frozen historical values · {history.some((task) => task.recovered) ? "Recovered snapshot" : "Latest available"}</small></div> : <Empty text="No end-of-day snapshot is available." />}</DashboardPanel>
    </div></section>;
}
function CompanyWorkloadChart({ departments, tasks }) {
  const workloadData = departments.map((department, index) => {
    const departmentTasks = tasks.filter((task) => task.departmentId === department.id);
    const workload = activeWorkload(departmentTasks);
    return { id: department.id, name: department.name || "Unknown", workload, color: WORKLOAD_COLORS[index % WORKLOAD_COLORS.length] };
  });
  const total = workloadData.reduce((sum, department) => sum + department.workload, 0);
  const circumference = 2 * Math.PI * 42;
  let offset = 0;

  return <div className="company-workload-content">
    <p className="company-workload-subtitle">Active workload distribution across departments</p>
    <div className="workload-donut-wrap">
      <svg className="workload-donut" viewBox="0 0 120 120" role="img" aria-label={`Total active workload: ${total}`}>
        <circle className="workload-donut-track" cx="60" cy="60" r="42" />
        {total > 0 && workloadData.map((department) => {
          const length = (department.workload / total) * circumference;
          const segment = <circle key={department.id} className="workload-donut-segment" cx="60" cy="60" r="42" pathLength="100" stroke={department.color} strokeDasharray={`${(length / circumference) * 100} 100`} strokeDashoffset={-(offset / circumference) * 100} />;
          offset += length;
          return segment;
        })}
        <text className="workload-donut-total" x="60" y="57" textAnchor="middle">{total}</text>
        <text className="workload-donut-label" x="60" y="68" textAnchor="middle">Total workload</text>
      </svg>
    </div>
    <div className="workload-legend" aria-label="Active workload distribution by department">
      {workloadData.map((department) => {
        const percentage = total > 0 ? (department.workload / total) * 100 : 0;
        const isHighest = total > 0 && department.workload === Math.max(...workloadData.map((item) => item.workload));
        return <div className={`workload-legend-row${isHighest ? " highest" : ""}`} key={department.id}>
          <span className="workload-legend-name"><i style={{ backgroundColor: department.color }} />{department.name}</span>
          <strong>{department.workload}</strong>
          <span>{percentage.toFixed(1)}%</span>
        </div>;
      })}
      {!workloadData.length && <p className="dashboard-empty">No department workload data.</p>}
    </div>
  </div>;
}
function Kpi({ label, value, detail, tone = "" }) { return <div className={`kpi-card ${tone}`}><span>{label}</span><strong>{value}</strong><small>{detail}</small></div>; }
function DashboardPanel({ title, children }) { return <section className="dashboard-panel"><div className="panel-heading"><h2>{title}</h2></div>{children}</section>; }
function Empty({ text }) { return <p className="dashboard-empty">{text}</p>; }
export default AdminDashboard;
