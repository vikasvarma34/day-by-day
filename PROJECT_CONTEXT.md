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
* **Shared Infrastructure**: Cross-cutting utilities live in bounded top-level folders: `src/config/`, `src/db/`, `src/errors/`, `src/logging/`, `src/middleware/`, `src/security/`, `src/validation/`.
* **Feature Depth**: Cohesive single-responsibility features stay flat (e.g. `src/auth/`). When a feature spans multiple distinct domains or use cases, group internally by responsibility:
  * `src/planner/domain/`: Pure planner calendar & recurrence rules (zero Express/PostgreSQL dependencies).
  * `src/planner/day/`: Authenticated Day API controller, repository, types, and unit tests.
  * `src/planner/planner.router.ts`: Top-level feature router composing sub-routes.
* **No Speculative Folders**: Do not create empty future folders (e.g. `later/`, `history/`) before their functionality is implemented.
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
      day/                 # Day view use case (controller, repository, types)
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
* **Environment Variables**: `DATABASE_URL`, `MIGRATION_DATABASE_URL`, `NODE_ENV`, `LOG_LEVEL`
```bash
cd backend
npm test
npm run build
npm run db:check
npm run user:create
npm run migrate
npm run migrate:create -- <migration_name>
```

### Android
```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
