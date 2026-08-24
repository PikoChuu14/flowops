import { useEffect, useMemo, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import { Skeleton } from "../components/ReportSkeleton";

const zoneToday = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kuala_Lumpur" }).format(new Date());
const initialPeriod = () => { const d = zoneToday().slice(0, 7).split("-").map(Number); return { year: d[0], month: d[1] }; };
const monthName = (month) => new Intl.DateTimeFormat("en", { month: "long" }).format(new Date(2020, month - 1, 1));
const shift = (year, month, delta) => { const date = new Date(year, month - 1 + delta, 1); return { year: date.getFullYear(), month: date.getMonth() + 1 }; };

export default function MonthlyReportsPage({ user, departments }) {
  const initial = useMemo(() => initialPeriod(), []);
  const [period, setPeriod] = useState(initial);
  const [departmentId, setDepartmentId] = useState(user.departmentId || departments[0]?.id || "");
  const [selectedUserId, setSelectedUserId] = useState(user.role === "STAFF" ? user.userId : null);
  const isManagement = user.role === "MANAGER" || user.role === "ADMIN";
  const showingOwn = Number(selectedUserId) === Number(user.userId);
  const [data, setData] = useState(null), [team, setTeam] = useState(null), [loading, setLoading] = useState(true), [error, setError] = useState(""), [saving, setSaving] = useState(false), [message, setMessage] = useState("");
  const [form, setForm] = useState({ monthlySummary: "", keyAchievements: "", blockers: "", nextMonthPlan: "" });
  const query = `year=${period.year}&month=${period.month}`;

  useEffect(() => { if (!departmentId && departments[0]?.id) setDepartmentId(departments[0].id); }, [departmentId, departments]);

  useEffect(() => {
    let cancelled = false; setLoading(true); setError("");
    const request = selectedUserId ? apiFetch(`/api/reports/monthly/${showingOwn ? "me" : `users/${selectedUserId}`}?${query}`) : apiFetch(`/api/reports/monthly/team?${query}&departmentId=${departmentId}`);
    request.then(async (r) => { if (!r.ok) throw new Error(r.status === 403 ? "You do not have access to this report." : "Unable to load monthly report."); return r.json(); }).then((value) => { if (cancelled) return; if (selectedUserId) { setData(value); setForm({ monthlySummary: value.report.monthlySummary || "", keyAchievements: value.report.keyAchievements || "", blockers: value.report.blockers || "", nextMonthPlan: value.report.nextMonthPlan || "" }); } else setTeam(value); setLoading(false); }).catch((e) => { if (!cancelled) { setError(e.message); setLoading(false); } });
    return () => { cancelled = true; };
  }, [period.year, period.month, departmentId, selectedUserId, showingOwn, query]);

  function navigate(delta) { setPeriod(shift(period.year, period.month, delta)); setMessage(""); }
  async function save(submit = false) {
    setSaving(true); setMessage(""); setError("");
    try {
      let response = await apiFetch(`/api/reports/monthly/me?${query}`, { method: "PUT", body: JSON.stringify(form) });
      if (!response.ok) throw new Error(response.status === 409 ? "This report is already submitted and read-only." : "Unable to save this report.");
      if (submit) { if (!window.confirm("Submit this monthly report?\n\nIt will become read-only after submission.")) return; response = await apiFetch(`/api/reports/monthly/me/submit?${query}`, { method: "POST" }); if (!response.ok) throw new Error("Unable to submit this report."); }
      const value = await response.json(); setData(value); setForm({ monthlySummary: value.report.monthlySummary || "", keyAchievements: value.report.keyAchievements || "", blockers: value.report.blockers || "", nextMonthPlan: value.report.nextMonthPlan || "" }); setMessage(submit ? "Monthly report submitted and frozen." : "Draft saved.");
    } catch (e) { setError(e.message); } finally { setSaving(false); }
  }
  async function exportPdf() { const path = showingOwn ? "/api/reports/monthly/me/pdf" : `/api/reports/monthly/users/${selectedUserId}/pdf`; const r = await apiFetch(`${path}?${query}`); if (!r.ok) { setError("Unable to generate PDF."); return; } const url = URL.createObjectURL(await r.blob()); const a = document.createElement("a"); a.href = url; a.download = `FlowOps-${data.employee.userName}-${period.year}-${period.month}-Monthly-Report.pdf`; a.click(); URL.revokeObjectURL(url); }
  async function exportTeamPdf() { const r = await apiFetch(`/api/reports/monthly/team/pdf?${query}&departmentId=${departmentId}`); if (!r.ok) { setError("Unable to generate department PDF."); return; } const url = URL.createObjectURL(await r.blob()); const a = document.createElement("a"); a.href = url; a.download = `FlowOps-${team.departmentName}-${period.year}-${period.month}-Monthly-Report.pdf`; a.click(); URL.revokeObjectURL(url); }

  const tabCount = selectedUserId && !showingOwn ? 3 : 2;
  const activeTabIndex = selectedUserId && !showingOwn ? 2 : selectedUserId ? 1 : 0;
  return <section className="dashboard-page monthly-reports-page">
    <div className="report-heading"><div><p className="eyebrow">FlowOps</p><h1>Monthly Reports</h1><p>{monthName(period.month)} {period.year}{selectedUserId && data ? ` · ${data.employee.userName}` : team ? ` · ${team.departmentName} Department` : ""}</p></div><div className="report-actions"><button className="secondary-button" aria-label="Previous month" onClick={() => navigate(-1)}>‹</button><strong className="month-picker-label">{monthName(period.month)} {period.year}</strong><button className="secondary-button" aria-label="Next month" onClick={() => navigate(1)}>›</button>{selectedUserId && data && <button className="secondary-button" onClick={exportPdf}>Export PDF</button>}</div></div>
    {isManagement && <div className="monthly-toolbar"><div className="monthly-toolbar-main">{user.role === "ADMIN" && <label className="date-control">Department<select value={departmentId} onChange={(e) => { setDepartmentId(e.target.value); setSelectedUserId(null); }}>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>}<div className="monthly-view-switcher" role="tablist" aria-label="Monthly report view" style={{ "--tab-count": tabCount, "--tab-index": activeTabIndex }}><span className="monthly-tab-highlight" aria-hidden="true" /><button role="tab" aria-selected={!selectedUserId} className={!selectedUserId ? "is-active" : ""} onClick={() => setSelectedUserId(null)}>Department Summary</button><button role="tab" aria-selected={showingOwn} className={showingOwn ? "is-active" : ""} onClick={() => setSelectedUserId(user.userId)}>My Monthly Report</button>{selectedUserId && !showingOwn && <button role="tab" aria-selected="true" className="is-active">Employee Report</button>}</div></div>{!selectedUserId && <button className="secondary-button" onClick={exportTeamPdf}>Export Department PDF</button>}</div>}
    {error && <p className="report-message">{error}</p>}{message && <p className="report-message success">{message}</p>}
    <div className={`monthly-report-content ${loading ? "is-loading" : "is-ready"}`} aria-busy={loading}>
      {loading ? <MonthlyReportSkeleton /> : selectedUserId && data ? <Individual data={data} form={form} setForm={setForm} canEdit={showingOwn && data.report.status !== "SUBMITTED"} saving={saving} save={save} /> : team ? <Team data={team} onOpen={(id) => setSelectedUserId(id)} /> : <p className="dashboard-empty">No monthly report data available.</p>}
    </div>
  </section>;
}

function MonthlyReportSkeleton() {
  return <div className="monthly-loading-skeleton" aria-label="Loading monthly report">
    <div className="monthly-kpis">{[1, 2, 3, 4].map((i) => <div className="monthly-kpi skeleton-panel" key={i}><Skeleton className="skeleton-line short" /><Skeleton className="skeleton-value" /></div>)}</div>
    <div className="monthly-skeleton-grid"><div className="monthly-task-section skeleton-panel"><Skeleton className="skeleton-line medium" />{[1, 2, 3].map((i) => <Skeleton className="skeleton-row" key={i} />)}</div><div className="monthly-task-section skeleton-panel"><Skeleton className="skeleton-line medium" />{[1, 2, 3].map((i) => <Skeleton className="skeleton-row" key={i} />)}</div></div>
    <div className="monthly-narrative skeleton-panel">{[1, 2, 3, 4].map((i) => <div key={i}><Skeleton className="skeleton-line short" /><Skeleton className="skeleton-textarea" /></div>)}</div>
  </div>;
}

function Individual({ data, form, setForm, canEdit, saving, save }) { return <><div className="monthly-kpis"><Kpi label="Completed Tasks" value={data.overview.completedCount} /><Kpi label="In Progress" value={data.overview.ongoingCount} /><Kpi label="Active Workload" value={data.overview.activeWorkload} /><Kpi label="Projects Worked On" value={data.overview.projectsWorkedOn} /></div><div className="monthly-task-columns"><TaskSection title="Work Completed" tasks={data.completedTasks} completed /><TaskSection title="Ongoing / Carry-over" tasks={data.ongoingTasks} /></div><div className="monthly-narrative"><Field label="Monthly Summary" name="monthlySummary" form={form} setForm={setForm} disabled={!canEdit} /><Field label="Key Achievements" name="keyAchievements" form={form} setForm={setForm} disabled={!canEdit} /><Field label="Challenges / Blockers" name="blockers" form={form} setForm={setForm} disabled={!canEdit} /><Field label="Next Month Plan" name="nextMonthPlan" form={form} setForm={setForm} disabled={!canEdit} />{canEdit && <div className="report-form-actions"><button className="secondary-button" disabled={saving} onClick={() => save(false)}>Save Draft</button><button className="primary-button" disabled={saving} onClick={() => save(true)}>Submit Monthly Report</button></div>}{!canEdit && <p className="report-help">{data.report.status === "SUBMITTED" ? "Submitted reports are read-only for staff." : "You can review this report, but only the employee can edit it."}</p>}</div></>; }
function Team({ data, onOpen }) { return <><div className="monthly-kpis"><Kpi label="Employees" value={data.summary.employees} /><Kpi label="Submitted" value={`${data.summary.submitted} / ${data.summary.employees}`} /><Kpi label="Completed Tasks" value={data.summary.completedTasks} /><Kpi label="Active / Carry-over" value={data.summary.ongoingTasks} /></div><div className="team-report-list">{data.employees.map((employee) => <article className="team-report-card" key={employee.userId}><div className="team-report-card-header"><div><h2>{employee.userName}</h2><span className={`report-status ${employee.status.toLowerCase()}`}>{employee.status === "SUBMITTED" ? "Submitted" : "Draft"}</span></div><button className="secondary-button" onClick={() => onOpen(employee.userId)}>Open employee report</button></div><div className="team-report-metrics"><span>Completed <b>{employee.completedCount}</b></span><span>Ongoing <b>{employee.ongoingCount}</b></span><span>Workload <b>{employee.activeWorkload}</b></span></div><details className="team-report-details"><summary>View monthly narrative</summary><div className="team-report-copy"><NarrativeBlock title="Monthly Summary" text={employee.monthlySummary} /><NarrativeBlock title="Key Achievements" text={employee.keyAchievements} /><NarrativeBlock title="Challenges / Blockers" text={employee.blockers} /><NarrativeBlock title="Next Month Plan" text={employee.nextMonthPlan} /></div></details></article>)}</div>{!data.employees.length && <p className="dashboard-empty">No staff found in this department.</p>}</>; }
function NarrativeBlock({ title, text }) { return <div><h3>{title}</h3><p>{text || "No update provided."}</p></div>; }
function Kpi({ label, value }) { return <div className="monthly-kpi"><span>{label}</span><strong>{value}</strong></div>; }
function Field({ label, name, form, setForm, disabled }) { return <label className="report-field"><span>{label}</span><textarea rows="4" maxLength="10000" value={form[name]} disabled={disabled} onChange={(e) => setForm({ ...form, [name]: e.target.value })} placeholder={`Add your ${label.toLowerCase()}…`} /></label>; }
function TaskSection({ title, tasks, completed }) { return <div className="monthly-task-section"><h2>{title}</h2>{tasks.length ? <ul>{tasks.map((task) => <li key={task.taskId}><span>{completed ? "✓ " : ""}{task.title}</span><small>{task.projectName} · {task.status}{task.workload == null ? "" : ` · workload ${task.workload}`}</small></li>)}</ul> : <p className="dashboard-empty">No recorded tasks for this month.</p>}</div>; }
