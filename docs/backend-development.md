# Backend Local Development & Operations Runbook

This runbook describes the standard workflow for configuring, initializing, running, and verifying the Day by Day backend locally.

---

## 1. Local Environment Configuration

Ensure `backend/.env` exists (copied/adapted from `backend/.env.example`):

```bash
# PostgreSQL Connections
DATABASE_URL=postgresql://user:password@localhost:5432/daybyday
MIGRATION_DATABASE_URL=postgresql://user:password@localhost:5432/daybyday

# Runtime Environment
NODE_ENV=development

# Persistent Local Development / Smoke Test User
TEST_USER_EMAIL=dev.user@example.com
TEST_USER_PASSWORD=DevPassword12345!
```

> **Note**: Real credentials stay in the gitignored `.env` file and are never committed.

---

## 2. Check Database Connectivity & Run Migrations

Verify database connection strings and ensure all schema migrations are applied:

```bash
cd backend

# Verify database connectivity
npm run db:check

# Apply pending schema migrations
npm run migrate
```

---

## 3. (Optional) Safe Development Database Reset

To wipe user-owned runtime data (tasks, schedules, completions, sessions, throttles, users) while strictly preserving schema, migration history, constraints, and indexes, pass `ALLOW_DEV_DB_RESET=true`:

```bash
ALLOW_DEV_DB_RESET=true npm run db:reset:dev
```

> **Safety Guard**: This script fails closed and aborts immediately unless `NODE_ENV === "development"` AND `ALLOW_DEV_DB_RESET === "true"`. Secondary checks also reject connection strings containing `"prod"`.

---

## 4. Seed the Persistent Development User

Create or verify the single local development test account:

```bash
npm run user:seed:dev
```

* Reuses standard email normalization, password validation, and Argon2id hashing.
* Idempotent: If the user already exists, it reports existing user details without error.

---

## 5. Start the Backend Server

Start the local server (default port `3000`):

```bash
# Start in development mode with live TS execution
npm run dev

# Or build and run production bundle
npm run build && npm start
```

---

## 6. Run the API Smoke Test

With the backend running, execute the one-command API smoke test in a separate terminal:

```bash
npm run smoke:api
```

The smoke test automatically:
1. Logs in with `TEST_USER_EMAIL` / `TEST_USER_PASSWORD`
2. Obtains a fresh session bearer token
3. Verifies `/auth/me`
4. Creates a Later task and verifies `/planner/later`
5. Creates a scheduled ONCE task and verifies `/planner/days/:date`
6. Creates an `INTERVAL_DAYS` recurring task
7. Edits/reschedules a task via `PATCH /tasks/:taskId`
8. Completes and undoes task completions
9. Verifies completed task appearance in `/planner/history`
10. Verifies coherent snapshot collection on `/planner/refresh`
11. Stops recurrence via `/tasks/:taskId/stop-recurrence`
12. Cleans up all smoke-test task rows
13. Logs out and proves the session token is invalidated

---

## 7. Run Full Automated Tests & Build

Execute the complete regression test suite and TypeScript build:

```bash
npm test
npm run build
```
