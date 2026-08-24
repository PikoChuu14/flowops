import { useEffect, useRef, useState } from "react";
import { useNotifications } from "../context/NotificationContext";
import NotificationPanel from "./NotificationPanel";

function NotificationBell({ onNavigate }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);
  const { notifications, unreadCount, loading, fetchNotifications, markRead, markAllRead, clearNotification, clearAll } = useNotifications();

  useEffect(() => {
    function close(event) {
      if (event.key === "Escape" || (event.type === "pointerdown" && !rootRef.current?.contains(event.target))) setOpen(false);
    }
    document.addEventListener("keydown", close);
    document.addEventListener("pointerdown", close);
    return () => { document.removeEventListener("keydown", close); document.removeEventListener("pointerdown", close); };
  }, []);

  async function toggle() {
    const next = !open;
    setOpen(next);
    if (next) await fetchNotifications();
  }

  async function openNotification(notification) {
    try {
      if (!notification.read) await markRead(notification.id);
    } catch (error) {
      console.error("Failed to mark notification as read:", error);
    } finally {
      setOpen(false);
      onNavigate(notification);
    }
  }

  return (
    <div className="notification-dock" ref={rootRef}>
      <button type="button" className="notification-bell" aria-label="Notifications" aria-expanded={open} onClick={toggle}>
        <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" /><path d="M10 21h4" /></svg>
        {unreadCount > 0 && <span className="notification-badge">{unreadCount > 99 ? "99+" : unreadCount}</span>}
      </button>
      {open && <NotificationPanel notifications={notifications} loading={loading} onOpen={openNotification} onMarkAllRead={markAllRead} onClear={clearNotification} onClearAll={clearAll} />}
    </div>
  );
}

export default NotificationBell;
