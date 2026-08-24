export function getNavigationItems(user) {
  const isAdmin = user?.role === "ADMIN";
  const canSeePpc = isAdmin || user?.departmentName?.toUpperCase() === "PPC";
  const canSeeTeam = user?.role === "MANAGER" || isAdmin;
  const canSeeProjects = canSeeTeam || user?.role === "STAFF";
  if (canSeePpc && !isAdmin) return [
    ["dashboard", "Dashboard"], ["personal", "My Kanban"], ["completed", "Completed Tasks"], ["ppc-arrivals", "Raw Material Arrival"], ["ppc-planning", "Planning"], ["project", "Projects"],
    ...(canSeeTeam ? [["staff", "Team"]] : []), ...(user?.role === "MANAGER" ? [["reviews", "Reviews"]] : []), ["report", "Monthly Reports"], ["desktop-notifications", "Desktop Notifications"],
  ];
  return [
    ["dashboard", "Dashboard"], ...(!isAdmin ? [["personal", "My Kanban"]] : []), ["completed", "Completed Tasks"], ...(canSeeProjects ? [["project", "Projects"]] : []), ...(canSeeTeam ? [["staff", "Team"]] : []),
    ...(user?.role === "MANAGER" ? [["reviews", "Reviews"]] : []), ["report", "Monthly Reports"], ["desktop-notifications", "Desktop Notifications"],
    ...(isAdmin ? [["users-admin", "Users"], ["data-management", "Data Management"], ["client-access", "Client Access"]] : []),
  ];
}
