# Troubleshooting

Java not installed: install a 64-bit Java 21 runtime and run setup again.

PostgreSQL or password errors: confirm the service is running, database/user names match `config\application.properties`, and the user can connect in pgAdmin. The server installer writes the exact failure to `C:\ProgramData\FlowOps\logs\installer-database.log` and displays the final error in the wizard. If PostgreSQL is already detected, choose **Use the PostgreSQL installation already on this computer** and enter that installation's `postgres` administrator password; do not choose automatic installation because both servers would compete for port 5432.

On a computer where setup fails but the same installer works elsewhere, check `Get-Service postgresql*` in PowerShell. A pre-existing PostgreSQL service is local machine state, not an installer-wide failure. FlowOps must either use it with the correct administrator password or it must be removed/reconfigured by the machine owner before automatic PostgreSQL setup.

Port 8080 in use: run `netstat -ano | findstr :8080`, stop the owning application, or change `server.port` and the firewall rule.

Phone cannot connect: confirm both devices are on the same LAN, use the server IPv4 from `ipconfig`, allow TCP 8080 through Windows Firewall, and do not use `localhost` on the phone.

Application errors: read `logs\flowops.log`. Backup errors usually mean PostgreSQL client tools are not installed or not on PATH.

Company address still shows **Not configured** after editing `C:\ProgramData\FlowOps\config\application.properties`: save a line such as `app.base-url=http://192.168.1.7:8080`, then restart the **FlowOps** Windows service from an elevated PowerShell or Services. FlowOps reads this setting at startup; refreshing the browser alone does not reload it. Current installers preserve an existing non-local company address when creating a new FlowOps database.
