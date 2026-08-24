import { useEffect, useState } from "react";
import NotificationBell from "./NotificationBell";
import FlowOpsLogo from "./FlowOpsLogo";
import ConnectionStatus from "./ConnectionStatus";
import { useNotifications } from "../context/NotificationContext";

const icons = {
  dashboard: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
  kanban: <><rect x="3" y="4" width="5" height="16" rx="1" /><rect x="10" y="4" width="5" height="10" rx="1" /><rect x="17" y="4" width="4" height="13" rx="1" /></>,
  team: <><circle cx="9" cy="8" r="3" /><circle cx="17" cy="9" r="2.5" /><path d="M3.5 19c.5-3 2.4-4.5 5.5-4.5s5 1.5 5.5 4.5M14 14.8c3.8-.9 6 .7 6.5 3.7" /></>,
  projects: <><path d="M3 7.5h7l2 2h9v9.5H3z" /><path d="M3 7.5V5h6l2 2.5" /></>,
  reviews: <><path d="M5 5h14v14H5z" /><path d="m8 12 2.5 2.5L16 9" /></>,
  report: <><path d="M6 3h9l3 3v15H6z" /><path d="M9 11h6M9 15h6M9 7h3" /></>,
  calendar: <><rect x="3" y="4.5" width="18" height="16" rx="2" /><path d="M16 2.5v4M8 2.5v4M3 9h18M8 13h.01M12 13h.01M16 13h.01M8 17h.01M12 17h.01" /></>,
  users: <><circle cx="9" cy="8" r="3"/><path d="M3 20c.5-4 2.5-6 6-6s5.5 2 6 6"/><path d="M17 8v6M14 11h6"/></>,
  settings: <><circle cx="12" cy="12" r="3"/><path d="M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.4 1A8 8 0 0 0 15 6l-.3-2.5h-4L10.4 6A8 8 0 0 0 9 7.1l-2.4-1-2 3.4 2 1.5a7 7 0 0 0 0 2l-2 1.5 2 3.4 2.4-1A8 8 0 0 0 10.4 18l.3 2.5h4L15 18a8 8 0 0 0 1.5-1.1l2.4 1 2-3.4-2-1.5c.1-.3.1-.7.1-1z"/></>,
  network: <><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8M12 17v4M8.5 10.5a5 5 0 0 1 7 0M10.5 12.5a2.2 2.2 0 0 1 3 0"/></>,
  menu: <><path d="M4 7h16M4 12h16M4 17h16" /></>,
  chevronLeft: <path d="m15 6-6 6 6 6" />,
  chevronRight: <path d="m9 6 6 6-6 6" />,
};

function Icon({ name, size = 19 }) {
  return <svg className="ui-icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{icons[name]}</svg>;
}

function AppShell({ user, activeView, onNavigate, onNotificationNavigate, onLogout, children }) {
  const { fetchUnreadCount } = useNotifications();
  const [collapsed, setCollapsed] = useState(true);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const isAdmin = user?.role === "ADMIN";
  const canSeePpc = isAdmin || user?.departmentName?.toUpperCase() === "PPC";
  const canSeeTeam = user?.role === "MANAGER" || user?.role === "ADMIN";
  const canSeeProjects = canSeeTeam || user?.role === "STAFF";
  const items = [
    { id: "dashboard", label: "Dashboard", icon: "dashboard" },
    ...(!isAdmin ? [{ id: "personal", label: "My Kanban", icon: "kanban" }] : []),
    ...(canSeeProjects ? [{ id: "project", label: "Projects", icon: "projects" }] : []),
    ...(canSeeTeam ? [{ id: "staff", label: "Team", icon: "team" }] : []),
    ...(user?.role === "MANAGER" ? [{ id: "reviews", label: "Reviews", icon: "reviews" }] : []),
    ...(!isAdmin ? [{ id: "report", label: user?.role === "STAFF" ? "Daily Report" : "Daily Reports", icon: "report" }] : []),
    ...(canSeePpc ? [{ id: "ppc-planning", label: "Planning", icon: "calendar" }] : []),
    ...(isAdmin ? [{ id: "users-admin", label: "Users", icon: "users" }, { id: "data-management", label: "Data Management", icon: "settings" }, { id: "client-access", label: "Client Access", icon: "network" }] : []),
  ];

  useEffect(() => {
    const closeOnEscape = (event) => { if (event.key === "Escape") setDrawerOpen(false); };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, []);

  const navigate = (view) => { onNavigate(view); setDrawerOpen(false); void fetchUnreadCount(); };
  return <div className={`app-shell ${collapsed ? "sidebar-collapsed" : ""}`}>
    <div className={`sidebar-overlay ${drawerOpen ? "is-visible" : ""}`} onClick={() => setDrawerOpen(false)} />
    <aside className={`sidebar ${drawerOpen ? "drawer-open" : ""}`}>
      <div className="sidebar-brand">
        <FlowOpsLogo className="brand-mark" decorative />
        <div className="brand-copy"><strong>FlowOps</strong></div>
      </div>
      <div className="sidebar-section-label">Workspace</div>
      <nav className="sidebar-nav" aria-label="Primary navigation">
        {items.map((item) => <button key={item.id} type="button" title={collapsed ? item.label : undefined} className={activeView === item.id ? "is-active" : ""} onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.label}</span>{activeView === item.id && <i />}</button>)}
      </nav>
      <div className="sidebar-spacer" />
      <div className="sidebar-footer">
        <div className="sidebar-profile" title={`${user?.name || user?.email || "User"} · ${user?.role || ""}${user?.departmentName ? ` · ${user.departmentName}` : ""}`}><div className="avatar">{(user?.name || user?.email || "K").slice(0, 1).toUpperCase()}</div><div className="profile-copy"><strong>{user?.name || user?.email || "User"}</strong><span>{user?.role}{user?.departmentName ? ` · ${user.departmentName}` : ""}</span></div></div>
        <button type="button" className="sidebar-logout" aria-label="Sign out" title={collapsed ? "Sign out" : undefined} onClick={onLogout}><span className="logout-symbol">↪</span><span>Sign out</span></button>
        <button type="button" className="sidebar-collapse" aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"} onClick={() => setCollapsed((value) => !value)}><Icon name={collapsed ? "chevronRight" : "chevronLeft"} /><span>{collapsed ? "Expand" : "Collapse"}</span></button>
      </div>
    </aside>
    <main className="app-main">
      <div className="header-actions">
        <ConnectionStatus />
        <NotificationBell onNavigate={onNotificationNavigate} />
      </div>
      <header className="mobile-topbar"><button type="button" className="btn-icon" aria-label="Open navigation" onClick={() => setDrawerOpen(true)}><Icon name="menu" /></button><div className="mobile-brand"><FlowOpsLogo className="brand-mark" decorative /><strong>FlowOps</strong></div><div className="avatar">{user?.name?.slice(0, 1)?.toUpperCase() || "U"}</div></header>
      <div className="app-content">{children}</div>
    </main>
  </div>;
}

export default AppShell;
