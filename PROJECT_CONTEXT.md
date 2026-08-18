# Project Context: Day by Day

## Overview
* **Product**: Day by Day — a small Android-only personal planner for one user.
* **Working Goal:** Use this repository to practise disciplined AI-assisted software engineering while building a small, well-engineered real application.
* **AI Functionality**: No AI functionality exists inside the application.

## Monorepo Boundaries
* `android/`
* `backend/`
* `docs/`

## Architecture & Technology Stack

### Android Decisions
* **Language & UI**: Kotlin, Jetpack Compose
* **Module Structure**: Single app module
* **State & Architecture**: Screen-level ViewModels, Room, minimal Repository/data layer
* **Networking & Serialization**: Retrofit + OkHttp, Kotlin serialization, Coroutines
* **SDK Versions**: `minSdk 31`, `targetSdk 37`, `compileSdk 37`

### Backend Decisions
* **Tech Stack**: Node.js (>= 24.x target) + TypeScript + Express
* **API Style**: REST / HTTPS / JSON

### Backend Architecture & Organization Rules
* **Feature-First Structure**: Organize code by feature module (e.g. `src/auth/`, `src/planner/`). Never create global `controllers/`, `repositories/`, `services/`, or `routes/` folders across the codebase.
* **Responsibility Flow (Controller → Service → Repository)**:
  * `Controller`: HTTP, authentication verification, request parsing, input validation boundary, status codes.
  * `Service`: Use-case / business orchestration, filtering, sorting, domain rule application, response mapping.
  * `Repository`: Parameterized PostgreSQL queries and data access only.
  * `Domain` (`src/planner/domain/`): Pure, reusable calendar rules and math (independent of Express and PostgreSQL).
  * Do not create decorative/empty layers without meaningful ownership.
* **Shared Infrastructure**: Cross-cutting utilities live in bounded top-level folders: `src/config/`, `src/db/`, `src/errors/`, `src/logging/`, `src/middleware/`, `src/security/`, `src/validation/`.
* **Data & Time Boundary**:
  * PostgreSQL `DATE` must be stored as strictly valid `YYYY-MM-DD` standard formats without arbitrary truncation.
  * System/server current time must never be implicitly assumed or injected as "today"; planner dates always arrive explicitly from client payloads or endpoints.
  * Avoid any runtime inference between UTC and local timezone dates; `plannerToday` solves timezone boundary concerns structurally by delegating local day definition entirely to the authenticated user.
* **POST /tasks Creation Rules**:
  * Authenticated endpoint
  * Client-generated task UUID v4
  * Race-safe insert-once creation
  * Same-owner retries return canonical state without overwrite
  * Scheduled creation is atomic
  * Recurring creation uses explicit plannerToday from the POST body
* **PATCH /tasks/:taskId Edit Rules**:
  * Authenticated endpoint
  * Task editing is an atomic operation locked with FOR UPDATE
  * Content edits (title, note, isImportant) are updated in-place
  * Rescheduling an uncompleted schedule updates it in-place
  * Changing a recurring schedule splits the segment (end-dates the existing schedule before the effective date, and creates a new schedule from the effective date)
* **Feature Depth**: Cohesive single-responsibility features stay flat (e.g. `src/auth/`). When a feature spans multiple distinct domains or use cases, group internally by responsibility:
  * `src/planner/domain/`: Pure planner calendar & recurrence rules (zero Express/PostgreSQL dependencies).
  * `src/planner/day/`: Authenticated Day API (controller, service, repository, types, colocated unit tests).
  * `src/planner/later/`: Authenticated Later API (controller, service, repository, types).
  * `src/planner/history/`: Authenticated History API (controller, service, repository, types, colocated unit tests).
  * `src/planner/tasks/`: Authenticated Task Creation & Mutation API (controller, service, repository, types).
  * `src/planner/refresh/`: Authenticated Manual Refresh / Snapshot API (controller, service, repository, types, colocated unit tests).
  * `src/planner/planner.router.ts`: Top-level feature router composing sub-routes.
* **Android API Contract**: The frozen backend API contract for Android is documented in `docs/planner-api-contract.md`.
* **No Speculative Folders**: Do not create empty future folders before their functionality is implemented.
* **Testing Convention**:
  * Unit tests are strictly colocated next to the source files they verify (`*.test.ts`).
  * Integration tests (database constraints, queries, and endpoint integration) live in `backend/tests/integration/`.
  * End-to-end HTTP/flow tests live in `backend/tests/e2e/`.

### Backend Repository Map
```text
backend/
  migrations/              # node-pg-migrate SQL migrations
  scripts/                 # Operational/setup utility scripts
  src/
    app.ts                 # Express application composition & global middleware
    server.ts              # Server bootstrapper & listener
    auth/                  # Authentication feature (controller, service, repository, middleware, types)
    config/                # Environment variable parsing & runtime config
    db/                    # PostgreSQL connection pool & lifecycle management
    errors/                # HttpError hierarchy & global error middleware
    logging/               # Structured JSON logger & request correlation middleware
    middleware/            # Content-type validation & common HTTP middleware
    planner/
      domain/              # Pure calendar math & recurrence evaluator (recurrence, date-validation)
      day/                 # Day view use case (controller, service, repository, types)
      later/               # Later view use case (controller, service, repository, types)
      history/             # History view use case (controller, service, repository, types)
      tasks/               # Task creation & mutation use case (controller, service, repository, types)
      refresh/             # Manual refresh & snapshot use case (controller, service, repository, types)
      planner.router.ts    # Feature-level router mounting planner endpoints
    security/              # Password hashing (Argon2id) & session crypto (SHA-256)
    types/                 # Express type declarations
    validation/            # Shared request parameter/body validation helpers
  tests/
    integration/           # Database integration & endpoint component tests
    e2e/                   # Full HTTP authentication & security E2E tests
```

### Database & Deployment
* **Database**: PostgreSQL (Production target: Supabase Free in Mumbai)
* **Backend Deployment Target**: Vercel Hobby (Mumbai where practical)

### Authentication & Security Fundamentals
* **Authentication**: Email/password, secure password hashing, opaque random server-side session tokens.
* **Security**:
  * Validate untrusted input
  * Enforce authorization server-side
  * Use parameterized database access
  * Keep secrets out of Git
  * Return safe errors
  * Use secure token storage

### Data Flow & Offline Strategy
* **Reads**: Normal reads come from Android local persistence (Room).
* **Sync & Writes**: Meaningful writes and manual refresh communicate with the backend.
* **Reminders**: Routine reminders are local Android behavior; no continuous server polling.

## Agent Rules
* Inspect relevant existing code before modifying it.
* Change only what the current task requires.
* Avoid unrelated refactors.
* Do not invent important requirements.
* Do not add dependencies without a concrete reason.
* Verify with appropriate builds/tests/runtime evidence.
* Inspect Git status/diff after changes.
* Never commit unless explicitly asked.

## Build/Test Commands

### Backend
* **Environment Variables**:
  * `DATABASE_URL`: Application connection pool string.
  * `MIGRATION_DATABASE_URL`: Migration runner connection string.
  * `NODE_ENV`: Runtime environment (`development` | `test` | `production`).
  * `LOG_LEVEL`: Optional logging severity (`debug` | `info` | `warn` | `error`).
  * `TEST_USER_EMAIL`: Email for the single persistent local development test user.
  * `TEST_USER_PASSWORD`: Password for the persistent local development test user.
* **Persistent Development User**: One local development test account is configured via `TEST_USER_EMAIL` and `TEST_USER_PASSWORD` in the gitignored `.env` file. Never commit credentials or bearer tokens. The automated smoke test logs in with these credentials to obtain a fresh session token automatically.
* **Runbook Reference**: See `docs/backend-development.md` for local setup, data reset, seed, and smoke workflows.

```bash
cd backend
npm test                 # Run unit, integration, and E2E regression suites
npm run build            # Compile TypeScript
npm run db:check         # Verify database connectivity
npm run db:reset:dev     # (Dev only) Reset runtime data while preserving schema/migrations
npm run user:create      # Interactive CLI user creation
npm run user:seed:dev    # Idempotent seed for persistent development user
npm run smoke:api        # Execute live API smoke test against running backend
npm run migrate          # Apply migrations
npm run migrate:create -- <migration_name>
```

### Android
```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
