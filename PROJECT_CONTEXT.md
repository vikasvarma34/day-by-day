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
