# todolist

A weekly task planner for the work week. Tasks are shown on a board with one
column per weekday (Monday to Friday), and you can move between weeks. Each
task has a headline, an optional description, a time estimate and a status
(**To Do → In Progress → In Review → Done**). Completed tasks are deleted
automatically after a month.

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

### 2. Run the app

```sh
./mvnw spring-boot:run          # Linux / macOS
.\mvnw.cmd spring-boot:run      # Windows
```

Then open <http://localhost:8080>. The server listens on `localhost` only,
so it isn't reachable from other machines.

### Build and test

```sh
./mvnw test       # run the tests (uses an in-memory H2 database, no PostgreSQL needed)
./mvnw package    # build target/javacalender-0.0.1-SNAPSHOT.jar
java -jar target/javacalender-0.0.1-SNAPSHOT.jar
```

### Configuration

Set in `src/main/resources/application.properties`:

| Property | Default | Meaning |
|---|---|---|
| `server.address` / `server.port` | `localhost` / `8080` | Where the app listens |
| `calendar.tasks.completed-retention` | `P1M` | How long Done tasks are kept ([ISO-8601 period](https://en.wikipedia.org/wiki/ISO_8601#Durations), e.g. `P14D`) |
| `calendar.tasks.cleanup-cron` | `0 0 * * * *` | When the cleanup runs (Spring cron: second, minute, hour, day, month, weekday). `-` disables it. |

## Technical overview

### Stack

| Layer | Technology |
|---|---|
| Backend | Java 25, Spring Boot 4 (Spring MVC) |
| Persistence | Spring Data JPA (Hibernate), PostgreSQL |
| Schema migrations | Flyway |
| Validation | Jakarta Bean Validation |
| Views | Thymeleaf, server-rendered HTML and CSS, a few lines of inline JavaScript |
| Tests | Spring Boot test slices, H2 in PostgreSQL compatibility mode |

### Project layout

```text
src/main/java/com/example/calendar/
├── JavacalenderApplication.java   entry point; enables scheduling; provides a Clock bean
├── task/
│   ├── Task.java                  JPA entity
│   ├── TaskStatus.java            TODO, IN_PROGRESS, IN_REVIEW, DONE (+ label and CSS class)
│   ├── TaskForm.java              form object with validation rules
│   ├── TaskRepository.java        Spring Data repository
│   └── TaskService.java           business logic, week calculation, scheduled cleanup
└── web/
    └── CalendarController.java    page and form endpoints
src/main/resources/
├── db/migration/V1__create_task.sql   Flyway migration
├── templates/calendar.html            the week board and create dialog
├── templates/fragments/task-card.html a single task card
└── static/css/calendar.css
```

### Data model

A single table, created by the Flyway migration `V1__create_task.sql`:

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

Hibernate runs with `ddl-auto=validate`: Flyway owns the schema, and the app
refuses to start if the entity and the table don't match.
`spring.jpa.open-in-view` is off, so all database access happens inside the
service's transactions.

### Request flow

The app uses the classic **Post/Redirect/Get** pattern with plain HTML
forms. No client-side framework is involved.

| Endpoint | What it does |
|---|---|
| `GET /?week=YYYY-MM-DD` | Shows the week containing that date (default: the current week) |
| `POST /tasks` | Creates a task |
| `POST /tasks/{id}/status` | Changes a task's status |
| `POST /tasks/{id}/date` | Moves a task to another weekday |
| `POST /tasks/{id}/delete` | Deletes a task |

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

### Testability

The current time always comes from an injected `java.time.Clock` bean, never
from `LocalDate.now()` directly, so tests can fix the clock to a known date.
The test profile (`src/test/resources/application.properties`) uses an
in-memory H2 database in PostgreSQL mode, so the real Flyway migration runs
against it, and disables the cleanup schedule.

- `TaskRetentionTests`: completed tasks older than the retention are deleted;
  newer ones and unfinished ones are kept.
- `CalendarControllerTests`: the web endpoints, including validation and
  redirects.
