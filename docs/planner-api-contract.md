# Planner Backend API Contract for Android

This document defines the frozen HTTP/JSON contract between the Android client and the Planner backend.

---

## 1. Authentication & Common Headers

* **Authentication**: All endpoints require a valid session Bearer token:
  ```http
  Authorization: Bearer <session_token>
  ```
* **Content Type**: Requests with a JSON body must include:
  ```http
  Content-Type: application/json
  ```
* **Error Response Format**: Standard RFC 7807 problem details / JSON error:
  ```json
  {
    "status": 400,
    "error": "BAD_REQUEST",
    "message": "Detailed error message"
  }
  ```

---

## 2. Date & Time Conventions

* **Planner Dates (`YYYY-MM-DD`)**: Calendar dates (`startDate`, `endDate`, `scheduledDate`, `completedDate`, `intervalAnchorDate`, `plannerToday`, `effectiveDate`) are plain ISO 8601 calendar strings. They are never shifted across timezones.
* **Planner Times (`HH:mm:ss`)**: Time of day (`scheduledTime`) is a plain 24-hour time string without timezone offsets.
* **Timestamps (`ISO 8601`)**: System audit times (`createdAt`, `updatedAt`, `completedAt`) are UTC ISO strings (`YYYY-MM-DDTHH:mm:ss.sssZ`).
* **`plannerToday`**: The client defines local today explicitly to decouple server time from user timezones. For security and invariant integrity, the server validates that `plannerToday` is within `±1` calendar day of the server's current UTC date (`[server UTC date - 1 day, server UTC date + 1 day]`). Server UTC is used exclusively for this plausibility guard, never for planner business calculations.

---

## 3. Endpoints Overview

| Method | Path | Description |
|---|---|---|
| `GET` | `/auth/me` | Current authenticated user profile |
| `PATCH` | `/auth/profile` | Update profile fields (`firstName`, `lastName`, `nickname`) |
| `POST` | `/auth/login` | Authenticate user credentials and issue session |
| `POST` | `/auth/logout` | Invalidate current session token |
| `POST` | `/auth/change-password` | Authenticated password change and session revocation |
| `GET` | `/planner/refresh` | Authoritative point-in-time snapshot for Room cache sync |
| `GET` | `/planner/days/:date` | Day view occurrences (`timed`, `anytime`, `completed`) |
| `GET` | `/planner/later` | Unscheduled, incomplete tasks |
| `GET` | `/planner/history` | Completed important ONCE and direct-Later tasks |
| `POST` | `/tasks` | Create new task (idempotent on UUID retry) |
| `PATCH` | `/tasks/:taskId` | Edit task metadata or schedule |
| `POST` | `/tasks/:taskId/complete` | Complete a task occurrence |
| `POST` | `/tasks/:taskId/undo` | Undo a task completion |
| `DELETE` | `/tasks/:taskId` | Hard delete task and cascade |
| `POST` | `/tasks/:taskId/stop-recurrence` | Stop future recurrence after `plannerToday` |

---

## 4. Endpoint Details

### 4.0 Profile Management (`PATCH /auth/profile`)
Allows the authenticated user to update their own profile fields (`firstName`, `lastName`, `nickname`). Omitted fields remain unchanged. `nickname: ""` or `nickname: null` clears the nickname to `null`.

* **Request**:
```json
{
  "firstName": "String (optional, non-blank)",
  "lastName": "String (optional, non-blank)",
  "nickname": "String | null (optional)"
}
```
* **Response (HTTP 200)**:
```json
{
  "user": {
    "id": "UUID",
    "email": "user@example.com",
    "firstName": "String",
    "lastName": "String",
    "nickname": "String | null"
  }
}
```

### 4.1 Manual Refresh (`GET /planner/refresh`)
Returns the complete canonical database snapshot under `REPEATABLE READ` isolation. Stream-capable chunked JSON.

* **Response (HTTP 200)**:
```json
{
  "tasks": [
    {
      "id": "UUID",
      "title": "String",
      "note": "String | null",
      "isImportant": true,
      "createdAt": "2026-08-18T10:00:00.000Z",
      "updatedAt": "2026-08-18T10:00:00.000Z"
    }
  ],
  "schedules": [
    {
      "id": "UUID",
      "taskId": "UUID",
      "scheduleType": "ONCE | INTERVAL_DAYS | WEEKDAYS",
      "startDate": "2026-08-18",
      "endDate": "2026-08-31 | null",
      "scheduledTime": "14:30:00 | null",
      "intervalDays": 1,
      "intervalAnchorDate": "2026-08-18 | null",
      "weekdaysMask": 31,
      "reminderMinutesBefore": 15,
      "createdAt": "2026-08-18T10:00:00.000Z",
      "updatedAt": "2026-08-18T10:00:00.000Z"
    }
  ],
  "completions": [
    {
      "id": "UUID",
      "taskId": "UUID",
      "scheduleId": "UUID | null",
      "scheduledDate": "2026-08-18 | null",
      "completedDate": "2026-08-18",
      "completedAt": "2026-08-18T10:30:00.000Z",
      "titleSnapshot": "String | null",
      "isImportantSnapshot": true
    }
  ]
}
```

---

### 4.2 Day View (`GET /planner/days/:date`)
* **Path Parameter**: `:date` (`YYYY-MM-DD`)
* **Response (HTTP 200)**:
```json
{
  "date": "2026-08-18",
  "timed": [ /* TaskOccurrenceItem (sorted ascending by scheduledTime) */ ],
  "anytime": [ /* TaskOccurrenceItem */ ],
  "completed": [ /* TaskOccurrenceItem */ ]
}
```
* **Occurrence Item**:
```json
{
  "taskId": "UUID",
  "scheduleId": "UUID",
  "title": "String",
  "note": "String | null",
  "isImportant": true,
  "scheduleType": "ONCE | INTERVAL_DAYS | WEEKDAYS",
  "scheduledDate": "2026-08-18",
  "scheduledTime": "09:00:00 | null",
  "reminderMinutesBefore": 15,
  "completion": {
    "id": "UUID",
    "completedDate": "2026-08-18",
    "completedAt": "2026-08-18T09:15:00.000Z"
  }
}
```

---

### 4.3 Later View (`GET /planner/later`)
* **Response (HTTP 200)**:
```json
{
  "items": [
    {
      "taskId": "UUID",
      "title": "String",
      "note": "String | null",
      "isImportant": false
    }
  ]
}
```

---

### 4.4 History View (`GET /planner/history`)
* Returns completed eligible Important ONCE scheduled tasks and direct-Later tasks with `completedDate <= plannerToday`.
* **Query Parameters**: `plannerToday=YYYY-MM-DD` (required), `q=string` (optional, max 200 characters)
* **Response (HTTP 200)**:
```json
{
  "groups": [
    {
      "date": "2026-08-17",
      "items": [
        {
          "taskId": "UUID",
          "completionId": "UUID",
          "title": "String (snapshot)",
          "completedAt": "2026-08-17T10:00:00.000Z"
        }
      ]
    }
  ]
}
```

---

### 4.5 Task Mutations

#### Create (`POST /tasks`)
* **Request**:
```json
{
  "id": "UUID v4",
  "title": "String (non-blank, max 255 characters)",
  "note": "String | null (max 500 characters)",
  "isImportant": false,
  "plannerToday": "YYYY-MM-DD (required if task has a schedule)",
  "schedule": {
    "type": "ONCE | INTERVAL_DAYS | WEEKDAYS",
    "startDate": "YYYY-MM-DD",
    "endDate": "YYYY-MM-DD | null",
    "scheduledTime": "HH:mm:ss | null",
    "intervalDays": 1,
    "weekdaysMask": 127,
    "reminderMinutesBefore": 0
  }
}
```
* **Notes**:
  - `title` is required, non-blank, and max 255 characters.
  - `note` is optional, max 500 characters.
  - For scheduled task creation, `plannerToday` is required.
  - Historical `ONCE` task creation (`schedule.startDate < plannerToday` and `schedule.type === 'ONCE'`) is allowed as backfill without reminders (`reminderMinutesBefore` must be absent/null).
  - Historical recurring task creation (`INTERVAL_DAYS`, `WEEKDAYS` where `schedule.startDate < plannerToday`) is blocked.
* **Response**: `HTTP 201 Created` (or `HTTP 200 OK` on idempotent retry with existing task):
```json
{
  "task": {
    "id": "UUID",
    "title": "String",
    "note": "String | null",
    "isImportant": false,
    "createdAt": "ISO 8601 string",
    "updatedAt": "ISO 8601 string",
    "schedules": [
      {
        "id": "UUID",
        "type": "ONCE | INTERVAL_DAYS | WEEKDAYS",
        "startDate": "YYYY-MM-DD",
        "endDate": "YYYY-MM-DD | null",
        "scheduledTime": "HH:mm:ss | null",
        "intervalDays": 1,
        "intervalAnchorDate": "YYYY-MM-DD | null",
        "weekdaysMask": 127,
        "reminderMinutesBefore": 0,
        "createdAt": "ISO 8601 string",
        "updatedAt": "ISO 8601 string"
      }
    ]
  }
}
```

#### Edit (`PATCH /tasks/:taskId`)
* **Request**:
```json
{
  "plannerToday": "YYYY-MM-DD (required if updating schedule)",
  "effectiveDate": "YYYY-MM-DD (required if updating schedule)",
  "title": "Updated Title (non-blank, max 255 characters)",
  "note": "Updated note (max 500 characters)",
  "isImportant": true,
  "schedule": { /* new schedule */ }
}
```
* **Notes**: For any schedule update (`ONCE`, `INTERVAL_DAYS`, `WEEKDAYS`), `plannerToday` and `effectiveDate` are required with `effectiveDate >= plannerToday` and `schedule.startDate >= plannerToday`. Rescheduling existing tasks into the past remains blocked. Content-only updates do not require `plannerToday`/`effectiveDate` and do not modify existing schedules. Changing `isImportant` on an already-completed eligible `ONCE` or direct-Later task atomically updates its History inclusion while keeping the original completion date and timestamp unchanged.
* **Response**: `HTTP 200 OK` with updated task and schedules.

#### Complete (`POST /tasks/:taskId/complete`)
* **Request**:
```json
{
  "plannerToday": "YYYY-MM-DD",
  "completedDate": "YYYY-MM-DD",
  "scheduleId": "UUID (required for scheduled task)",
  "scheduledDate": "YYYY-MM-DD (required for scheduled task)"
}
```
* **Notes**:
  - `plannerToday` is required and represents the client's current calendar date.
  - For scheduled tasks: `completedDate` must satisfy `min(scheduledDate, plannerToday) <= completedDate <= plannerToday`. Historical backfill may use `completedDate = scheduledDate` in the past. Early completion of future scheduled tasks on `plannerToday` is supported.
  - For direct Later tasks: `completedDate` must equal `plannerToday`.
  - When `completedDate <= plannerToday`, an important eligible completion immediately becomes visible in History under that completion date.
* **Response**: `HTTP 200 OK` (idempotent):
```json
{
  "id": "UUID",
  "taskId": "UUID",
  "scheduleId": "UUID | null",
  "scheduledDate": "YYYY-MM-DD | null",
  "completedDate": "YYYY-MM-DD",
  "completedAt": "ISO 8601 string",
  "titleSnapshot": "string | null",
  "isImportantSnapshot": true
}
```

#### Undo (`POST /tasks/:taskId/undo`)
* **Request**:
```json
{
  "scheduleId": "UUID (optional)",
  "scheduledDate": "YYYY-MM-DD (optional)"
}
```
* **Response**: `HTTP 200 OK` (idempotent) `{ "success": true }`.

#### Delete (`DELETE /tasks/:taskId`)
* **Response**: `HTTP 200 OK` `{ "deletedTaskId": "UUID" }` (idempotent, does not leak cross-owner task existence).

#### Stop Recurrence (`POST /tasks/:taskId/stop-recurrence`)
* **Request**:
```json
{
  "plannerToday": "YYYY-MM-DD"
}
```
* **Response**: `HTTP 200 OK` (or `HTTP 409 Conflict` if a completion exists strictly after `plannerToday`).

---

## 5. Known Limitations

* History/refresh responses are currently unpaginated in V1 and may require pagination for large long-lived datasets.
