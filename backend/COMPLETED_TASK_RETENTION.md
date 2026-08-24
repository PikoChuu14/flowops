# Completed task retention

`Task.completedAt` records the completion event and is set only when a task enters `DONE`; reopening clears it, so a later completion gets a fresh timestamp. Active Kanban queries use one shared cutoff: the start of the Kuala Lumpur calendar date seven days before today. Thus a task completed any time on 17 August remains visible on 24 August, and tasks completed before 17 August are hidden.

The active-board rule is query-only. It never deletes or changes a task's status, and the completed-history endpoint queries all `DONE` tasks with backend pagination. Reports and snapshots continue to use their existing task queries.

The schema is managed by the existing Hibernate `ddl-auto=update` setup. No reliable completion-event history was found for pre-existing `DONE` rows, so their nullable `completedAt` is intentionally left null rather than inventing a historical completion time. Such legacy rows remain visible on active boards for compatibility and are always available in completed history; newly completed and reopened tasks follow the precise seven-day rule. A future reviewed data-migration can backfill legacy rows if a trustworthy source is identified.

Change the default later with `DONE_VISIBLE_DAYS` or `app.tasks.done-visible-days`; the initial value is 7 for every department.
