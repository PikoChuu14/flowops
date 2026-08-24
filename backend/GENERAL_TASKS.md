# General task schema evolution

Phase PPC-1 reuses the existing `tasks` table and task workflow.

- `tasks.column_id` becomes nullable. A null column identifies a general task and guarantees it is not returned by project-column queries.
- `tasks.department_id` is a new nullable foreign key. It is populated for general tasks and is deliberately left null for existing/project tasks, whose department continues to come from `column -> board -> department`.
- `task_snapshots.board_id` and `task_snapshots.board_name` become nullable. General snapshots retain task, status, workload, assignee, creator, and department values without inventing a project.

These are additive/backward-compatible changes for the existing `ddl-auto=update` deployment. No existing rows are converted or deleted. Before a production rollout, back up the database and verify that Hibernate removes the old `NOT NULL` constraints from `tasks.column_id`, `task_snapshots.board_id`, and `task_snapshots.board_name`.
