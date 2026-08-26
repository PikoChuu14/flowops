export function getNavigationItems(user) {
  const isAdmin = user?.role === "ADMIN";
  const canSeePpc = isAdmin || user?.departmentName?.toUpperCase() === "PPC";
  const canSeeTeam = user?.role === "MANAGER" || isAdmin;
  const canSeeProjects = canSeeTeam || user?.role === "STAFF";
  const canSeeRequests = isAdmin || user?.departmentName?.toUpperCase() === "RDD" || user?.departmentName?.toUpperCase() === "PPC";
  if (canSeePpc && !isAdmin) return [
    ["dashboard", "Dashboard"], ["personal", "My Kanban"], ["project", "Projects"], ["ppc-arrivals", "Raw Material Arrival"], ["ppc-planning", "Planning"], ["requests", "Requests"], ["completed", "Completed Tasks"],
    ...(canSeeTeam ? [["staff", "Team"]] : []), ...(user?.role === "MANAGER" ? [["reviews", "Reviews"]] : []), ["report", "Monthly Reports"], ["desktop-notifications", "Desktop Notifications"],
  ];
  return [
    ["dashboard", "Dashboard"], ...(!isAdmin ? [["personal", "My Kanban"]] : []), ["completed", "Completed Tasks"], ...(canSeeRequests ? [["requests", "Requests"]] : []), ...(canSeeProjects ? [["project", "Projects"]] : []), ...(canSeeTeam ? [["staff", "Team"]] : []),
    ...(user?.role === "MANAGER" ? [["reviews", "Reviews"]] : []), ["report", "Monthly Reports"], ["desktop-notifications", "Desktop Notifications"],
    ...(isAdmin ? [["users-admin", "Users"], ["data-management", "Data Management"], ["client-access", "Client Access"]] : []),
  ];
}
