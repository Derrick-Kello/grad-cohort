# Graduate Research Progress System

Built from `Graduate_Research_Progress_System_—_Product_Requirements_Document.pdf` (v1.0).

- `grps-api/`: Spring Boot 3.5 API, PostgreSQL, Flyway. Module layout follows PRD section 26.
- `gradsystem/`: Next.js 16 web app with the persistent sidebar, role switcher and role screens from section 18.
- `docker-compose.yml`: the PRD's local services (Postgres, MinIO, MailHog, ClamAV).

## Run it locally

Requirements: JDK 17 or newer, Node 20+, PostgreSQL 16+ (or `docker compose up postgres`).

```bash
# 1. Database (skip if you use docker compose)
createuser grps --pwprompt        # password: grps
createdb grps --owner grps

# 2. API on http://localhost:8080 (Swagger UI at /swagger-ui.html)
cd grps-api
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 3. Web app on http://localhost:3000
cd gradsystem
npm install
npm run dev
```

The `local` profile seeds a demo graduate school the first time it starts on an empty database. Every account uses the password `Graduate2026`:

| Sign in as | Role | What you'll see |
|---|---|---|
| `admin@grps.local` | Admin | Verification queue, scheduling board, a flagged student to review |
| `superadmin@grps.local` | Super Admin | Same as admin, plus granting admin roles |
| `secretary@grps.local` | Secretary | A presentation from yesterday waiting for its outcome |
| `librarian@grps.local` | Librarian | Two publications in the review queue |
| `lecturer@grps.local` | Lecturer and Supervisor | Role switcher, GRS 701 where one student is at 60% attendance, supervisees |
| `student@grps.local` | Student (Masters) | Presentation 3 passed and waiting for evidence |
| `adwoa.nyarko@grps.local` | Student | All presentations approved, thesis submission open |
| `esi.badu@grps.local` | Student | Flagged for expulsion |

Emails are logged to the API console in the local profile instead of being sent. Set `MAIL_ENABLED=true` to send through SMTP (MailHog on port 1025 from the compose file).

### Dev tools (local profile, admin only)

These move the injected clock and run jobs on demand, so you can walk a student through 24 months in minutes:

```bash
POST /api/v1/dev/clock            {"offsetDays": 548}      # jump ahead 18 months; {"offsetDays": 0} resets
POST /api/v1/dev/jobs/deadline-evaluation                  # also: milestone-unlock, presentation-reminders, ...
POST /api/v1/dev/screening/next   {"similarityPct": 23, "aiPct": 5}   # next stub screening result
```

## Tests

```bash
cd grps-api && ./mvnw test          # 44 unit tests, named after business rules (BR_03_..., FR_AUTH_02_...)
cd gradsystem && npm run lint && npx next build
```

The PRD's eight critical journeys (section 25) run end to end against a live API with `grps-api/scripts/journeys.py` (55 checks, all passing). It changes data and moves the clock, so run it on a freshly seeded database:

```bash
mkdir -p /tmp/grps-files && python3 grps-api/scripts/make_docs.py /tmp/grps-files
python3 grps-api/scripts/journeys.py /tmp/grps-files
```

## How the PRD maps to the code

| PRD area | Backend | Screens |
|---|---|---|
| Sign-in, lockout, reset, activation, role switch (7) | `user/`, `security/` | `/login`, `/activate`, `/reset-password`, top bar |
| Users, CSV import, supervisors, programmes, templates, semesters, cohorts (7) | `user/`, `student/`, `programme/` | Admin › Students, Staff, Programmes |
| Scheduling, clashes, outcomes, attempts, sequential gating (8) | `milestone/` | Admin › Scheduling, Secretary › Calendar and Record Outcomes |
| Evidence, verification, dashboard, achievements, statement PDF (9) | `evidence/`, `milestone/`, `dashboard/` | Student › Evidence, Dashboard, Achievements; Admin › Verification Queue |
| Publications and librarian review, DOI lookup (10) | `publication/` | Student › Publications; Librarian › Review Queue |
| Thesis upload, format check, screening, override (11) | `thesis/` | Student › Thesis; Admin › Theses |
| Attendance and exam eligibility (12) | `attendance/` | Lecturer screens; Admin › Courses; Student › Attendance |
| Notifications, live unread count, digest, announcements (13) | `notification/` | Bell, `/notifications`, Admin › Settings |
| Deadlines, at-risk, expulsion review (14) | `deadline/` | Admin › At-Risk & Expulsion |
| Reports, audit, settings (15) | `report/`, `audit/`, `settings/` | Admin › Reports, Audit Log, Settings |

Every business rule in section 16 is enforced in a service method, not only in the UI.

## Where this differs from the PRD

- **Java 17.** The PRD names JDK 21; only 17 was installed here. The code compiles on 21 unchanged. Change `java.version` in `pom.xml` once 21 is available (the Dockerfile already builds on 21).
- **File storage is local disk** behind the `FileStore` interface. The S3/MinIO implementation is not written yet.
- **Screening provider is a stub.** No Turnitin/iThenticate adapter exists until the university confirms API access (PRD risk 1). The manual-entry path (FR-THS-09) works.
- **DTO mapping is by hand**, not MapStruct.
- **Format checker reads DOCX XML directly** instead of through Apache POI, which avoids POI's version-sensitive low-level API. PDFBox handles PDFs.
- **Testcontainers integration tests are not written**; Docker was not running on this machine.
- **Tokens are kept in browser storage.** Fine for v1 behind HTTPS, but an httpOnly-cookie session is safer.
- Not built: QR self check-in (FR-ATT-11, Could), achievement badges beyond simple labels, the CI pipeline, OWASP/Trivy scans, k6 load tests.

## Open questions from the PRD (section 29)

Each one is a setting or isolated in one place, so answers change configuration, not code:
checkpoint count per programme (Programmes), separate similarity and AI limits (Settings, already separate),
format rules (Programmes › Thesis format rules), minimum publications before thesis (Programmes),
mandatory evidence for PhD (Programmes), maximum attempts (Settings).
