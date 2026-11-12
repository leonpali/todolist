# todolist

A weekly task planner for the work week. Tasks are shown on a board with one
column per weekday (Monday to Friday), and you can move between weeks. Each
task has a headline, an optional description, a time estimate and a status
(**To Do → In Progress → In Review → Done**). Completed tasks are deleted
automatically after a month.

Every user has their own account and sees only their own tasks. Accounts are
identified by email address, passwords are stored as salted BCrypt hashes, and
the app is served over HTTPS only.

The Maven artifact and database are named `javacalender`.

## Features

- **Week board**: five columns, Monday to Friday, with today's column
  highlighted. Each column shows its number of tasks and total estimated
  hours.
- **Week navigation**: previous and next week, plus the ISO week number and
  date range in the header.
- **Create tasks** in a dialog: headline (required, up to 120 characters),
  description (up to 2,000 characters), estimated hours (0.25 to 40) and
  status. The date defaults to today if you're looking at the current week,
  otherwise to that week's Monday.
- **Edit tasks on the card**: change the status with a dropdown or move a
  task to another day with a date picker. Both save immediately.
- **Delete tasks**, after a confirmation prompt.
- **Weekdays only**: tasks can't be scheduled on a Saturday or Sunday.
- **Automatic cleanup**: tasks that have been Done for more than a month are
  deleted. The check runs every hour.

### Accounts

- **Registration** at `/register` with an email address, a password and a
  password confirmation. After registering you are sent to the login page.
- **Login** at `/login` with email and password. Email addresses are
  case-insensitive (`Alice@Example.com` and `alice@example.com` are the same
  account), and leading/trailing spaces are ignored.
- **Logout** with the button in the board's header, next to your email
  address. Logging out ends the session and deletes the session cookie.
- **Private boards**: you only see, edit, move and delete your own tasks.
  Requests for another user's task get a `404 Not Found`, as if the task
  didn't exist.
- **Login required**: every page except login and registration redirects to
  `/login` if you aren't logged in.

**Password rules**: 8 to 64 characters, at least one letter and one digit.
Only ASCII letters, digits and these special characters are allowed:
`! @ # $ % ^ & * ( ) _ + = [ ] { } : , . ? ~ | / -`. Spaces, quotes, `;`,
`< >`, backslashes and non-ASCII characters are rejected.

There is no password reset, password change or account deletion in the UI
yet.

## Getting started

### Requirements

- **JDK 25**
- **PostgreSQL** (any recent version)

Maven doesn't need to be installed: the repository includes the Maven
Wrapper.

### 1. Create the database

By default the app connects to `localhost:5432`, database `javacalender`,
with user and password `javacalender`.

With Docker:

```sh
docker run -d --name javacalender-db \
  -e POSTGRES_DB=javacalender \
  -e POSTGRES_USER=javacalender \
  -e POSTGRES_PASSWORD=javacalender \
  -p 5432:5432 postgres:17
```

Or in an existing PostgreSQL installation:

```sql
CREATE USER javacalender WITH PASSWORD 'javacalender';
CREATE DATABASE javacalender OWNER javacalender;
```

You don't need to create any tables: Flyway creates the schema on first
startup.

To use a different database, set these environment variables:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/javacalender` |
| `DB_USERNAME` | `javacalender` |
| `DB_PASSWORD` | `javacalender` |

### 2. Create a TLS certificate

The app only runs over HTTPS. For local development, generate a self-signed
certificate (it isn't checked in; `src/main/resources/ssl/*.p12` is in
`.gitignore`). `keytool` comes with the JDK:

```sh
mkdir -p src/main/resources/ssl
keytool -genkeypair -alias todolist -keyalg RSA -keysize 3072 -validity 825 \
  -storetype PKCS12 -keystore src/main/resources/ssl/dev-keystore.p12 \
  -storepass changeit -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1"
```

Without this file the app fails to start. In production, point
`SSL_KEY_STORE` at a real certificate instead (see [Configuration](#configuration)).

### 3. Run the app

```sh
./mvnw spring-boot:run          # Linux / macOS
.\mvnw.cmd spring-boot:run      # Windows
```

Then open <https://localhost:8443>. Your browser will warn about the
self-signed certificate; accept it for local use. Create an account via
**Create one** on the login page, then log in. The server listens on
`localhost` only, so it isn't reachable from other machines.

### Build and test

```sh
./mvnw test       # run the tests (uses an in-memory H2 database, no PostgreSQL or certificate needed)
./mvnw package    # build target/javacalender-0.0.1-SNAPSHOT.jar
java -jar target/javacalender-0.0.1-SNAPSHOT.jar
```

### Configuration

Set in `src/main/resources/application.properties`:

| Property | Default | Meaning |
|---|---|---|
| `server.address` / `server.port` | `localhost` / `8443` (env `PORT`) | Where the app listens |
| `server.ssl.key-store` | `classpath:ssl/dev-keystore.p12` (env `SSL_KEY_STORE`) | PKCS12 keystore with the TLS certificate |
| `server.ssl.key-store-password` | `changeit` (env `SSL_KEY_STORE_PASSWORD`) | Keystore password |
| `server.ssl.key-alias` | `todolist` (env `SSL_KEY_ALIAS`) | Alias of the certificate in the keystore |
| `app.security.require-https` | `true` | Redirect plain HTTP requests to HTTPS |
| `app.security.bcrypt-strength` | `12` | BCrypt cost factor (each +1 doubles the hashing time) |
| `calendar.tasks.completed-retention` | `P1M` | How long Done tasks are kept ([ISO-8601 period](https://en.wikipedia.org/wiki/ISO_8601#Durations), e.g. `P14D`) |
| `calendar.tasks.cleanup-cron` | `0 0 * * * *` | When the cleanup runs (Spring cron: second, minute, hour, day, month, weekday). `-` disables it. |

## Technical overview

### Stack

| Layer | Technology |
|---|---|
| Backend | Java 25, Spring Boot 4 (Spring MVC) |
| Security | Spring Security (form login, sessions, CSRF protection, BCrypt) |
| Persistence | Spring Data JPA (Hibernate), PostgreSQL |
| Schema migrations | Flyway |
| Validation | Jakarta Bean Validation |
| Views | Thymeleaf, server-rendered HTML and CSS, a few lines of inline JavaScript |
| Tests | Spring Boot test slices, H2 in PostgreSQL compatibility mode |

### Project layout

```text
src/main/java/com/example/calendar/
├── JavacalenderApplication.java   entry point; enables scheduling; provides a Clock bean
├── security/
│   ├── SecurityConfig.java        filter chain, login/logout, HTTPS redirect, BCrypt encoder
│   └── ValidatingAuthenticationProvider.java  rejects malformed passwords before any lookup
├── user/
│   ├── AppUser.java               JPA entity (email is the primary key)
│   ├── CredentialRules.java       email/password rules and email normalization
│   ├── RegistrationForm.java      form object with validation rules
│   ├── UserRepository.java        Spring Data repository
│   ├── UserService.java           registration; loads users for Spring Security
│   └── EmailAlreadyRegisteredException.java
├── task/
│   ├── Task.java                  JPA entity
│   ├── TaskStatus.java            TODO, IN_PROGRESS, IN_REVIEW, DONE (+ label and CSS class)
│   ├── TaskForm.java              form object with validation rules
│   ├── TaskRepository.java        Spring Data repository (all lookups filter by owner)
│   ├── TaskNotFoundException.java mapped to 404; also used for other users' tasks
│   └── TaskService.java           business logic, week calculation, scheduled cleanup
└── web/
    ├── AuthController.java        login and registration pages
    └── CalendarController.java    page and form endpoints
src/main/resources/
├── db/migration/V1__create_task.sql   Flyway migration: task table
├── db/migration/V2__create_user_and_task_owner.sql  user table, task owner
├── ssl/dev-keystore.p12               local TLS certificate (not in git, see above)
├── templates/calendar.html            the week board and create dialog
├── templates/fragments/task-card.html a single task card
├── templates/login.html               login page
├── templates/register.html            registration page
└── static/css/calendar.css
```

### Data model

Two tables, created by the Flyway migrations.

`app_user` (`V2__create_user_and_task_owner.sql`):

| Column | Type | Notes |
|---|---|---|
| `email` | `VARCHAR(254)` | primary key, stored trimmed and lower-cased |
| `password_hash` | `VARCHAR(100)` | BCrypt hash (`$2a$12$…`), salt included; never the plain password |
| `created_at` | `TIMESTAMPTZ` | |

`task` (`V1__create_task.sql`, owner added in `V2`):

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | primary key, generated in Java |
| `headline` | `VARCHAR(120)` | |
| `description` | `VARCHAR(2000)` | empty string if not given |
| `estimated_hours` | `DOUBLE PRECISION` | |
| `status` | `VARCHAR(20)` | the enum name, e.g. `IN_PROGRESS` |
| `scheduled_date` | `DATE` | indexed: the board queries by date range |
| `created_at` | `TIMESTAMPTZ` | tasks in a column are ordered by this |
| `completed_at` | `TIMESTAMPTZ` | set when the status becomes Done; indexed for the cleanup |
| `owner_email` | `VARCHAR(254)` | foreign key to `app_user.email`, `ON DELETE CASCADE`; indexed together with `scheduled_date` |

Each task belongs to exactly one user, and a user can have any number of
tasks. Deleting a user deletes their tasks. Tasks created before the `V2`
migration have no owner and don't appear on anyone's board.

Hibernate runs with `ddl-auto=validate`: Flyway owns the schema, and the app
refuses to start if the entity and the table don't match.
`spring.jpa.open-in-view` is off, so all database access happens inside the
service's transactions.

### Request flow

The app uses the classic **Post/Redirect/Get** pattern with plain HTML
forms. No client-side framework is involved.

| Endpoint | What it does |
|---|---|
| `GET /login` | Login page (public) |
| `POST /login` | Logs in (handled by Spring Security; fields `email`, `password`) |
| `GET /register` | Registration page (public) |
| `POST /register` | Creates an account, then redirects to `/login?registered` |
| `POST /logout` | Logs out, then redirects to `/login?logout` |
| `GET /?week=YYYY-MM-DD` | Shows the week containing that date (default: the current week) |
| `POST /tasks` | Creates a task |
| `POST /tasks/{id}/status` | Changes a task's status |
| `POST /tasks/{id}/date` | Moves a task to another weekday |
| `POST /tasks/{id}/delete` | Deletes a task |

All task endpoints require a logged-in user and act on that user's tasks
only. The owner always comes from the session (`Principal`), never from a
request parameter. Every `POST` must carry Spring Security's CSRF token,
which Thymeleaf adds to the forms automatically.

Every form sends the currently shown week along, and every successful `POST`
redirects back to `/?week=<that week's Monday>`. Reloading the page therefore
never submits a form twice, and you stay on the week you were looking at.
After creating a task, you are taken to the week the new task is in.

If the create form is invalid, the controller renders the page directly
(without a redirect) with the dialog reopened and the error messages shown
next to the fields.

The status dropdown and date picker on each card submit their form on
`change`, so edits save without a separate button.

### Business rules

- **Weekdays only.** `TaskForm.isOnWeekday()` is an `@AssertTrue` rule
  ("Pick a weekday"), and the date-change endpoint and `TaskService` check it
  again, so the rule can't be bypassed with a hand-crafted request.
- **Weeks start on Monday.** `TaskService.weekStart(date)` uses
  `TemporalAdjusters.previousOrSame(MONDAY)`. `tasksForWeek` loads Monday to
  Friday in a single query and groups the tasks by day.
- **Completion time.** `Task.changeStatus` sets `completed_at` when a task
  becomes Done and clears it when the task moves back to any other status.
  Re-saving a task that is already Done keeps the original time.
- **Retention.** `purgeExpiredCompletedTasks()` is a `@Scheduled` job that
  deletes every task whose `completed_at` is older than the retention period,
  with one bulk `DELETE` query.

### Security

- **Password storage.** Passwords are hashed with `BCryptPasswordEncoder`
  (cost 12). BCrypt generates a random salt for every hash and stores it in
  the hash string, so the same password never produces the same hash twice.
- **Input validation.** `CredentialRules` defines the email and password
  rules in one place. They are checked in the registration form (Bean
  Validation), again in `UserService.register`, and at login:
  `ValidatingAuthenticationProvider` rejects a password that breaks the rules
  and `UserService.loadUserByUsername` rejects a malformed email, both before
  the database is queried. Database access goes through JPA with bound
  parameters in any case.
- **Generic login errors.** A wrong password and an unknown email both show
  the same "Invalid email or password" message.
- **No password echo.** After a failed registration the password fields are
  cleared rather than sent back to the browser.
- **HTTPS only.** Plain HTTP requests are redirected to HTTPS, HTTPS responses
  carry an HSTS header, and the session cookie is `Secure`, `HttpOnly` and
  `SameSite=Strict`.
- **Ownership checks.** `TaskService` loads tasks with
  `findByIdAndOwner_Email`, so a task ID belonging to someone else behaves
  exactly like an unknown ID (`TaskNotFoundException`, 404).

### Testability

The current time always comes from an injected `java.time.Clock` bean, never
from `LocalDate.now()` directly, so tests can fix the clock to a known date.
The test profile (`src/test/resources/application.properties`) uses an
in-memory H2 database in PostgreSQL mode, so the real Flyway migrations run
against it. It also disables the cleanup schedule and the HTTPS redirect
(MockMvc sends plain HTTP), and lowers the BCrypt cost to 4 to keep the tests
fast.

- `TaskRetentionTests`: completed tasks older than the retention are deleted;
  newer ones and unfinished ones are kept.
- `CalendarControllerTests`: the web endpoints, including validation and
  redirects.
- `CredentialValidationTests`: the email and password rules, including
  rejected injection characters, and the registration form's validation.
- `UserStorageAndHashingTests`: users are stored under their normalized
  email, duplicates are rejected, and passwords are stored only as salted
  BCrypt hashes.
- `AuthenticationTests`: registration, login, logout, CSRF protection and
  the redirect to the login page.
- `HttpsEnforcementTests`: HTTP is redirected to HTTPS and HSTS is sent only
  over HTTPS.
- `TaskOwnershipTests`: each user sees and can change only their own tasks,
  the owner can't be spoofed, and deleting a user deletes their tasks.
