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
* **`plannerToday`**: The client defines local today explicitly to decouple server time from user timezones.

---

## 3. Endpoints Overview

| Method | Path | Description |
|---|---|---|
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
* **Query Parameters**: `plannerToday=YYYY-MM-DD` (required), `search=string` (optional)
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
  "title": "String",
  "note": "String | null",
  "isImportant": false,
  "plannerToday": "YYYY-MM-DD (required if recurring schedule)",
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
* **Response**: `HTTP 201 Created` (or `HTTP 200 OK` on idempotent retry with existing task).

#### Edit (`PATCH /tasks/:taskId`)
* **Request**:
```json
{
  "plannerToday": "YYYY-MM-DD",
  "effectiveDate": "YYYY-MM-DD",
  "title": "Updated Title",
  "note": "Updated note",
  "isImportant": true,
  "schedule": { /* new schedule or null to clear */ }
}
```
* **Response**: `HTTP 200 OK` with updated task and schedules.

#### Complete (`POST /tasks/:taskId/complete`)
* **Request**:
```json
{
  "completedDate": "YYYY-MM-DD",
  "scheduleId": "UUID (required for scheduled task)",
  "scheduledDate": "YYYY-MM-DD (required for scheduled task)"
}
```
* **Response**: `HTTP 200 OK` (idempotent).

#### Undo (`POST /tasks/:taskId/undo`)
* **Request**:
```json
{
  "scheduleId": "UUID (optional)",
  "scheduledDate": "YYYY-MM-DD (optional)"
}
```
* **Response**: `HTTP 200 OK` (idempotent).

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
