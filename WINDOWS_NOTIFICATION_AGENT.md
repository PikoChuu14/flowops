# FlowOps Windows Notification Agent

The agent is a self-contained .NET 8 WinForms tray process using Windows App SDK app notifications. Notifications remain in Windows Notification Center until the user opens or clears them; the legacy tray balloon is used only when the Windows app-notification API is unavailable. It reads `%LOCALAPPDATA%\FlowOps Client\server-url.txt`, polls every 30 seconds while connected, and backs off through 30 seconds, 1 minute, 2 minutes, and 5 minutes while offline.

## Registration and security

1. Sign in to FlowOps and open **Desktop Notifications**.
2. Select **Enable on this PC**. The code expires in 10 minutes and works once.
3. Open **Settings** from the FlowOps tray icon and enter the code.
4. The backend returns a revocable device credential once. It stores only the SHA-256 hash; the agent encrypts the credential with Windows DPAPI for the current Windows user.

The agent never receives a password, browser JWT, or refresh token. Its plaintext cursor is non-sensitive. Account status is checked on every poll. A revoked device or disabled/pending user is rejected; the agent clears its encrypted credential and shows **Authentication required**.

The backend supplies the notification title, message, and relative destination. The agent does not calculate task, review, raw-material, due-date, or assignment rules. Clicking a notification invokes the existing `FlowOps-Client.ps1` launcher, which prefers Edge app mode, then Chrome app mode, then the default browser. Current Windows versions surface the native WinForms `NotifyIcon` notification through the Windows notification UI.

## Exact VM manual test plan

### A. Install

1. Run the central FlowOps server on the host and note its reachable URL.
2. On the VM, install the refreshed `FlowOps-Client-Setup.exe`.
3. Keep **Start FlowOps Notification Agent when I sign in to Windows** checked.
4. Enter the host server URL and finish setup.
5. Verify the FlowOps tray icon appears.
6. Verify `HKCU\Software\Microsoft\Windows\CurrentVersion\Run\FlowOps Notification Agent` exists.

### B. Register

7. Open FlowOps and sign in as Bob.
8. Open **Desktop Notifications**.
9. Select **Enable on this PC** and note the code.
10. Right-click the tray icon, select **Settings**, enter the code, and select **Register this PC**.
11. Verify the tray says **Notification Status: Connected**.
12. Refresh the browser page and verify the VM/device name is listed.

### C. Close FlowOps

13. Completely close the FlowOps window.
14. Close Edge and Chrome.

### D. Create and click a notification

15. From another session, assign a task to Bob or trigger Bob's delayed raw-material notification.
16. Within 30 seconds, verify a Windows notification appears.
17. Click it and verify FlowOps opens at the backend-provided destination.
18. Close FlowOps, wait another polling interval, and verify the same notification does not appear again.

### E. Restart

19. Restart the VM without manually opening FlowOps.
20. Verify the agent starts and reaches **Connected**.
21. Generate another notification and verify it appears.

### F. Offline and configuration reload

22. Disconnect/stop the server. Verify the agent remains running, shows **Disconnected**, and sends no repeated failure notifications.
23. Restore the server and verify automatic reconnection.
24. Use **Configure FlowOps Client** to change the URL; verify the next poll or **Reconnect / Refresh** uses it.

### G. Revoke

25. Revoke the VM device in FlowOps.
26. Within one poll, verify **Authentication required**.
27. Generate another notification and verify none arrives until re-registration.

## Manual Windows verification still required

Notification/Action Center presentation varies with Windows version and user notification settings. Confirm toast click behavior, startup, installer upgrade/file replacement, and uninstall on the exact company Windows image. Validate trusted HTTPS certificates before broad rollout. Plain HTTP is supported only for development and leaves credentials and notification content unencrypted in transit.
