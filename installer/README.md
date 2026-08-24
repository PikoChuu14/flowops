# FlowOps installer

## Lightweight client and notification agent

Before compiling `FlowOps-Client.iss`, publish the self-contained Windows agent:

```powershell
.\scripts\publish-agent.ps1
& 'C:\Program Files (x86)\Inno Setup 6\ISCC.exe' .\installer\FlowOps-Client.iss
```

The client package remains per-user and installs no Java, PostgreSQL, backend, service, or system-wide .NET runtime. Its default-checked auto-start option uses the current user's HKCU Run key. Upgrades preserve the server URL, DPAPI-encrypted credential, cursor, logs, and previous task selections. Uninstall removes binaries and the Run entry but deliberately leaves per-user data and the backend device registration intact; revoke the device in FlowOps first if access should end.

Use trusted HTTPS in production. HTTP remains available for development/LAN testing but does not encrypt credentials or notification content in transit.

This installer uses Inno Setup and WinSW. The repository intentionally does not include third-party binaries.

Before compiling, place these files in `installer\prerequisites\`:

- `WinSW-x64.exe`, renamed to `FlowOps.exe` in the build payload.
- Official PostgreSQL Windows installer, named `postgresql-installer.exe`, so the installer can offer automatic PostgreSQL installation.

The build script creates a private Java 21 runtime with `jlink` from the developer JDK, prepares the JAR and helper scripts, then calls `ISCC.exe`. It does not download Java, PostgreSQL, or WinSW; you must place the approved prerequisite binaries before building. It does not store credentials in installer source.

Runtime binaries install under `C:\Program Files\FlowOps`. Writable data is kept under `C:\ProgramData\FlowOps` and is deliberately retained on uninstall. The PostgreSQL administrator password is passed through a temporary setup file, used to create `flowops` and `flowops_user` on fresh installs, then deleted. Application credentials are stored only in the protected ProgramData config directory.

The Inno Setup `AppId` is the permanent product GUID `{8B58D1C2-7FD1-4CF7-9B49-0B2AE24C1A4E}`. It is intentionally unchanged so the rebrand upgrades the same application instead of creating a second product registration. The installer separately detects the installed app (uninstall registry key, current or legacy service, and files) and FlowOps data (retained configuration plus a query of the application `users` table). When FlowOps data exists, `Use existing database` is the safe default and preserves the existing database name and user, database password, JWT secret, bootstrap state, backups, metadata, and application data. `Start with a new database` requires confirmation, creates a `pg_dump` backup under `C:\ProgramData\FlowOps\backups`, archives the old database with a timestamped name, and only then creates a fresh `flowops` database.

The first administrator page is skipped when the database query finds an existing `ADMIN`; otherwise it is shown. Same-version installs are repair installs, older installed versions are upgrades, and an uninstalled app with retained data is a reinstall with existing data. The finished page waits for the health endpoint before offering to open the application; timeout provides Retry and Open Logs without opening the browser automatically.
