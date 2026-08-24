import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import { useAuth } from "./AuthContext";

const NotificationContext = createContext(null);
const API_BASE_URL = "";
const POLLING_INTERVAL_MS = 15000;

export function NotificationProvider({ children }) {
  const { isAuthenticated, user } = useAuth();
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const fetchSequence = useRef(0);
  const countSequence = useRef(0);
  const mutationVersion = useRef(0);
  const notificationRequest = useRef(null);
  const countRequest = useRef(null);
  const unreadIds = useRef(new Set());
  const authKey = isAuthenticated ? user?.userId : null;
  const authKeyRef = useRef(authKey);
  // A refresh can race with the PATCH that marks a notification as read. Keep
  // the local read decision authoritative until the server has caught up.
  const readOverrides = useRef(new Set());

  function beginMutation() {
    mutationVersion.current += 1;
    fetchSequence.current += 1;
    countSequence.current += 1;
    notificationRequest.current = null;
    countRequest.current = null;
  }

  useEffect(() => {
    authKeyRef.current = authKey;
    mutationVersion.current += 1;
    fetchSequence.current += 1;
    countSequence.current += 1;
    notificationRequest.current = null;
    countRequest.current = null;
    readOverrides.current.clear();
    unreadIds.current.clear();
    setNotifications([]);
    setUnreadCount(0);
    setLoading(false);
  }, [authKey]);

  const fetchUnreadCount = useCallback(async () => {
    if (authKey == null) return;
    if (countRequest.current?.authKey === authKey) return countRequest.current.promise;

    const requestSequence = ++countSequence.current;
    const versionAtStart = mutationVersion.current;
    const promise = (async () => {
      try {
        const response = await apiFetch(`${API_BASE_URL}/api/notifications/unread-count`);
        if (!response.ok) throw new Error(`Unread notification count request failed (${response.status}).`);
        const data = await response.json();
        if (authKeyRef.current === authKey
            && requestSequence === countSequence.current
            && versionAtStart === mutationVersion.current) {
          setUnreadCount(Number(data.count) || 0);
        }
      } catch (error) {
        if (authKeyRef.current === authKey) console.error("Failed to load unread notification count:", error);
      } finally {
        if (countRequest.current?.promise === promise) countRequest.current = null;
      }
    })();
    countRequest.current = { authKey, promise };
    return promise;
  }, [authKey]);

  const fetchNotifications = useCallback(async () => {
    if (authKey == null) return;
    if (notificationRequest.current?.authKey === authKey) return notificationRequest.current.promise;

    const requestSequence = ++fetchSequence.current;
    const versionAtStart = mutationVersion.current;
    setLoading(true);
    const promise = (async () => {
      try {
        const response = await apiFetch(`${API_BASE_URL}/api/notifications?limit=30`);
        if (!response.ok) throw new Error(`Notifications request failed (${response.status}).`);
        const data = await response.json();
        // Opening the panel can overlap a read/clear mutation. Never let a
        // response that began before that mutation restore stale local state.
        if (authKeyRef.current === authKey
            && requestSequence === fetchSequence.current
            && versionAtStart === mutationVersion.current) {
          data.forEach((item) => {
            if (item.read) readOverrides.current.delete(item.id);
          });
          const merged = data.map((item) => readOverrides.current.has(item.id) ? { ...item, read: true } : item);
          unreadIds.current = new Set(merged.filter((item) => !item.read).map((item) => item.id));
          setNotifications(merged);
        }
        await fetchUnreadCount();
      } catch (error) {
        if (authKeyRef.current === authKey) console.error("Failed to load notifications:", error);
      } finally {
        if (notificationRequest.current?.promise === promise) notificationRequest.current = null;
        if (authKeyRef.current === authKey && requestSequence === fetchSequence.current) setLoading(false);
      }
    })();
    notificationRequest.current = { authKey, promise };
    return promise;
  }, [authKey, fetchUnreadCount]);

  useEffect(() => {
    if (authKey == null) return undefined;

    const refreshWhenVisible = () => {
      if (document.visibilityState === "visible") void fetchUnreadCount();
    };
    void fetchUnreadCount();
    const interval = window.setInterval(refreshWhenVisible, POLLING_INTERVAL_MS);
    window.addEventListener("focus", refreshWhenVisible);
    document.addEventListener("visibilitychange", refreshWhenVisible);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener("focus", refreshWhenVisible);
      document.removeEventListener("visibilitychange", refreshWhenVisible);
    };
  }, [authKey, fetchUnreadCount]);

  const markRead = useCallback(async (id) => {
    beginMutation();
    readOverrides.current.add(id);
    if (unreadIds.current.delete(id)) setUnreadCount((count) => Math.max(0, count - 1));
    setNotifications((items) => items.map((item) => item.id === id ? { ...item, read: true } : item));
    try {
      const response = await apiFetch(`${API_BASE_URL}/api/notifications/${id}/read`, { method: "PATCH" });
      if (!response.ok) throw new Error(`Mark notification as read failed (${response.status}).`);
      const persisted = await response.json();
      readOverrides.current.delete(id);
      setNotifications((items) => items.map((item) => item.id === id ? persisted : item));
      await fetchUnreadCount();
      return true;
    } catch (error) {
      readOverrides.current.delete(id);
      await fetchNotifications();
      throw error;
    }
  }, [fetchNotifications, fetchUnreadCount]);

  const markAllRead = useCallback(async () => {
    beginMutation();
    // Keep the optimistic decision for every currently visible item while the
    // PATCH is in flight. A refresh started after this mutation can otherwise
    // briefly restore the old unread values from the server.
    setNotifications((items) => {
      items.forEach((item) => readOverrides.current.add(item.id));
      return items.map((item) => ({ ...item, read: true }));
    });
    unreadIds.current.clear();
    setUnreadCount(0);
    try {
      const response = await apiFetch(`${API_BASE_URL}/api/notifications/read-all`, { method: "PATCH" });
      if (!response.ok) throw new Error(`Mark all notifications as read failed (${response.status}).`);
      await fetchNotifications();
      await fetchUnreadCount();
      return true;
    } catch (error) {
      readOverrides.current.clear();
      await fetchNotifications();
      throw error;
    }
  }, [fetchNotifications, fetchUnreadCount]);

  const clearNotification = useCallback(async (id) => {
    beginMutation();
    readOverrides.current.delete(id);
    if (unreadIds.current.delete(id)) setUnreadCount((count) => Math.max(0, count - 1));
    setNotifications((items) => items.filter((item) => item.id !== id));
    const response = await apiFetch(`${API_BASE_URL}/api/notifications/${id}`, { method: "DELETE" });
    if (!response.ok) fetchNotifications();
  }, [fetchNotifications]);

  const clearAll = useCallback(async () => {
    beginMutation();
    readOverrides.current.clear();
    unreadIds.current.clear();
    setUnreadCount(0);
    setNotifications([]);
    const response = await apiFetch(`${API_BASE_URL}/api/notifications`, { method: "DELETE" });
    if (!response.ok) fetchNotifications();
  }, [fetchNotifications]);

  const value = useMemo(() => ({
    notifications,
    unreadCount,
    loading,
    fetchNotifications,
    fetchUnreadCount,
    markRead,
    markAllRead,
    clearNotification,
    clearAll,
  }), [notifications, unreadCount, loading, fetchNotifications, fetchUnreadCount, markRead, markAllRead, clearNotification, clearAll]);

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
}

export function useNotifications() { return useContext(NotificationContext); }
