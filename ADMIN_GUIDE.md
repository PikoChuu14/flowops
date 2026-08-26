# Administrator guide

Log in with the first administrator created by setup. Existing authorization boundaries remain in force: only an `ADMIN` can manage company-wide accounts or database backups.

## Client Access and PWA installation

Open **Client Access** in the administrator sidebar to see whether the company address is configured and usable, the activation-link base, and all suitable detected LAN IPv4 candidates. Detected addresses are diagnostics or temporary client-testing values only; FlowOps never promotes one to the permanent company address. Before employee onboarding, configure a stable internal DNS hostname such as `http://flowops-server:8080`, a reserved/static server IP, or a trusted HTTPS hostname as `APP_BASE_URL`/`app.base-url`.

Install the full `FlowOps-Setup-x.x.x.exe` package on the single server machine only. During HTTP LAN deployment, install `FlowOps-Client-Setup.exe` on Windows staff PCs and enter the central server URL. The launcher uses Edge/Chrome app mode and stores only that URL. Android/browser PWA installation and iPhone/iPad Safari **Share → Add to Home Screen** remain available. Client devices do not install PostgreSQL or a second backend.

## User Management

Open **Users** in the administrator sidebar. The page lists all accounts and can be filtered by Active or Disabled; search matches names, usernames, and email addresses.

To onboard an employee:

1. Select **Add User**.
2. Enter the employee's name and a unique username or email address. A short username such as `afiq` works for an in-house account; the first administrator created by setup continues to use the email entered in the installer.
3. Choose a department from the live departments table and select `STAFF`, `MANAGER`, or `ADMIN` (the default is `STAFF`).
4. Enter and confirm an initial password of at least eight characters, then save. The account is active immediately and no activation link or `APP_BASE_URL` setting is required.

If an employee forgets their password, select **Set Password**, enter a new password twice, and give the new password to the employee through an approved company channel. Existing passwords cannot be displayed because FlowOps stores only secure password hashes. Setting a password for an older pending-activation account activates it and invalidates its old activation token. Disabled accounts stay disabled when their password is changed.

The company address is still used for client rollout information and any legacy activation links. Moving the server between Wi-Fi, Ethernet, or a phone hotspot does not change it. To configure an installed server, edit `C:\ProgramData\FlowOps\config\application.properties`, set `app.base-url=http://flowops-server:8080`, restart the **FlowOps** service, and verify the value on **Client Access**. It is no longer required to create or reset staff credentials.

Use **Edit** to change a user's name, department, role, or status. Department and role changes do not rewrite historical tasks or reports. Use **Disable** for offboarding and **Reactivate** to restore access. Accounts are deliberately not deleted: old task ownership and reporting attribution must remain intact. The application refuses to disable or demote the final active administrator.

## Data Management

Open **Settings**, then **Backups & Archives**. `ADMIN` users can:

- create a PostgreSQL custom-format backup immediately;
- download/export an individual `.backup` or `.dump` file;
- open the configured backup folder when the browser is running on the Windows server;
- view installer-archived database names as read-only restore sources;
- permanently delete an unused backup after confirmation; and
- request a controlled restore.

Installed backups are stored under `C:\ProgramData\FlowOps\backups`. Development uses `backups-dev` unless `DEV_BACKUP_DIRECTORY` is set. The configured directory is normalized and download/delete operations accept filenames only, preventing arbitrary filesystem access.

Restore is intentionally delegated to the installed Windows helper. Before queuing it, FlowOps creates a new `PRE_RESTORE` safety backup. The helper then stops the `FlowOps` service, runs `pg_restore`, restarts the service, and writes status under the ProgramData runtime directory. If helper execution fails, the safety backup is retained. Restore is disabled in development and automated tests.
