# V1 Requirements: Day by Day

## Core Product Requirements
* **Single User Focus**: Day by Day is a personal planner app designed for one user.
* **Today View**:
  * Displays only tasks deliberately scheduled for that date.
  * Timed tasks are ordered chronologically by time.
  * Dated tasks without a time appear in an `Anytime` section.
  * Important/starred tasks remain in their normal chronological position.
  * Completed tasks move to a `Completed` section.
  * Incomplete tasks never automatically roll over to another day.
* **Later View**:
  * Contains unscheduled items.
  * Unscheduled items never automatically appear in Today.
* **History View**:
  * Contains completed important/starred past tasks.

## Task Capabilities
* **Task Fields**: Title, optional short note, date, optional time, optional reminder, important/starred flag, completion status.
* **Task Actions**: Creation, editing, rescheduling, completion, deletion.
* **Deletion Confirmation**: Deleting a task requires confirmation.

## Recurrence Rules
* **Supported Patterns**: Every day, every N days, selected weekdays.
* **Start and End Dates**: Recurrence has a required start date and may have an optional end date.
* **Cross-Week Recurrence**: Every-N-days recurrence continues seamlessly from its start date across week boundaries.
* **Schedule Edits**: Editing a recurring schedule affects future scheduling while past/completed history remains unchanged.
* **Stopping Recurrence**: Stopping/deleting a recurring schedule stops future occurrences in one action while preserving past/completed history.
* **Single Occurrence Limitation**: V1 does not support editing only one isolated recurring occurrence.

## Data Persistence & Sync Strategy
* **Local Persistence**: Normal screen reads come from Android local persistence so the app does not require a live server for every screen.
* **Manual Refresh**: A manual Refresh pulls current server state and updates local data.
* **Backend Writes**: Meaningful writes (create, edit, complete, reschedule, delete) go through the backend; sophisticated offline write queues and conflict resolution are not required in V1.
* **Local Reminders**: Routine reminders are handled locally on Android.
* **No Continuous Polling**: App does not perform continuous server polling for reminders.

## Scope Exclusions (Deliberate Out-of-Scope for V1)
* No AI features or automatic planning.
* No team or multi-user collaboration.
* No projects, tags, or subtasks.
* No complex priority scoring systems.
* No Google Calendar or third-party integrations.
* No unnecessary enterprise infrastructure.
