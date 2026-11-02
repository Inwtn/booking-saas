# Slotwise — Booking SaaS

A first version of a multi-tenant scheduling application for small businesses. Built with Java 17, Spring Boot, Spring Security, JDBC, Flyway, and PostgreSQL. The browser interface and documentation are in English.

## Run locally

With Docker and Docker Compose installed:

    docker compose up --build

Open http://localhost:8082. Create a workspace, sign in, add a resource or professional, and book an appointment. New installations contain no users or customer data.

Alternatively, start only the database and use a local Java 17/Maven installation:

    docker compose up -d db
    mvn spring-boot:run

## First-version scope

- Workspace registration creates an owner account atomically.
- Owners add resources and new staff accounts.
- Owners and staff list, create, and cancel appointments in their workspace.
- Every workspace operation verifies membership on the server.
- Resources are linked to bookings with a composite workspace foreign key.
- PostgreSQL exclusion constraints reject overlapping confirmed appointments, including concurrent requests.
- Adjacent appointments are allowed; cancellations release the time slot.
- Times are stored as UTC instants and displayed in the browser's local time zone.

This release is an internal team scheduling MVP. It does not yet include self-service customer accounts, invitations, password recovery, service catalogs, opening hours, subscriptions, notifications, or payment processing. Staff accounts must use an email not already registered; attaching existing accounts is reserved for a future invitation flow.

## Architecture

Same-origin HTML/JavaScript -> Spring MVC -> transactional application service -> JDBC -> PostgreSQL.

Shared tables use workspace IDs. Application authorization checks each request's authenticated user against workspace membership. This is application-level tenant isolation, not PostgreSQL row-level security. A composite foreign key prevents a booking from referencing another workspace's resource. A GiST exclusion constraint on the resource and the half-open time range [start,end) enforces scheduling consistency in the database.

Sessions use HttpOnly, SameSite=Lax cookies. CSRF protection remains enabled for registration, login, logout, and mutations. The UI fetches a CSRF token and refreshes it after authentication. Passwords use BCrypt; raw passwords are never stored. No authentication tokens are stored in localStorage.

## API overview

| Method | Endpoint | Access |
| --- | --- | --- |
| GET | /api/csrf | Public |
| POST | /api/register | Public, CSRF required |
| POST | /api/login | Form username/password, CSRF required |
| POST | /api/logout | Session, CSRF required |
| GET | /api/me | Authenticated |
| GET | /api/workspaces/{id}/resources | Member |
| POST | /api/workspaces/{id}/resources | Owner |
| POST | /api/workspaces/{id}/staff | Owner |
| GET | /api/workspaces/{id}/bookings?from=...&to=... | Member |
| POST | /api/workspaces/{id}/bookings | Member |
| POST | /api/workspaces/{id}/bookings/{booking}/cancel | Member |

Booking requests contain resourceId (UUID), customerName, startsAt, and endsAt (ISO 8601 timestamps with offsets). Starts must be in the future, ends must follow starts, and duration cannot exceed eight hours. Listing requests cover at most 31 days and return at most 1,000 entries. Pagination is planned before higher-volume use.

## Verification

    mvn verify

Integration tests use a real PostgreSQL 16 container through Testcontainers. They check session login, CSRF, tenant boundaries, role restrictions, cross-workspace resource references, cancellation, adjacent time slots, and concurrent booking attempts. Docker must be running. GitHub Actions provides the same Maven verification.

## Configuration and deployment

DATABASE_URL, DATABASE_USER, and DATABASE_PASSWORD configure the database. Local Compose defaults are development-only and bind ports to loopback. For HTTPS deployment set COOKIE_SECURE=true, supply database credentials externally, and configure a reverse proxy. The Flyway migration requires permission to create the btree_gist extension. Sessions are currently stored in one app instance's memory; multiple replicas require shared sessions.

Before exposing a public service, add account verification, abuse protection/rate limits, password recovery, an invitation flow, backups, and operational monitoring. There is no billing integration in this version.

## Next milestones

1. Service durations, opening hours, and staff availability.
2. Invitations, account verification, and password recovery.
3. Customer booking page and email notifications.
4. Plan limits, billing integration, deployment, and monitoring.

## References

- [PostgreSQL range types and exclusion constraints](https://www.postgresql.org/docs/16/rangetypes.html)
- [Spring Security CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

## License

MIT
