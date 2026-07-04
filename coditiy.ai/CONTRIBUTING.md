# CONTRIBUTING.md — Contributor Guidelines

---

## 🛠️ 1. Local Development Workflow

Follow this workflow to set up your environment and make changes:

### Prerequisites
- JDK 17
- Node.js (v18+)
- Local PostgreSQL instance running on port 5432 (or run PostgreSQL via Docker)

### Startup Sequence

1. **Database**: Start only the database container if you wish to run the app code natively:
   ```bash
   docker compose up -d scheduler-postgres
   ```
2. **Backend**: Run the Spring Boot application using Maven:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=api,worker
   ```
3. **Frontend**: Install dependencies and launch the React development server:
   ```bash
   cd dashboard
   npm install
   npm run dev
   ```

---

## 🏗️ 2. Coding Standards

### Backend (Java)
- Use standard Java camelCase naming conventions.
- Annotate entities with JPA annotations. Keep database constraints (`NOT NULL`, `UNIQUE`) synchronized with validation annotations (e.g. `@NotNull`, `@Size`).
- Keep business logic in the service layer (`service/`), data exposure in controllers (`controller/`), and database interaction in repositories (`repository/`).
- Use Lombok annotations (`@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Builder`, `@Slf4j`) to keep code concise and maintainable.
- Ensure all exceptions are registered and formatted inside `GlobalExceptionHandler.java`.

### Frontend (React & TypeScript)
- Use standard functional components with hooks.
- Prefix hook custom names with `use` (e.g. `useToast`).
- Write interfaces for all model schemas returning from the backend DTO layer.
- Never use direct `alert()` or `confirm()` prompts. Use the custom toast notification hook or `<ConfirmDialog />` modal.
- Ensure state variables that are no longer used are removed to prevent TypeScript strict mode compilation failures.

---

## 🌿 3. Branch Naming Conventions

When contributing new features or bug fixes, name your branches with clear descriptors:

- **Features**: `feature/feature-name` (e.g. `feature/jwt-remember-me`)
- **Bug Fixes**: `bugfix/issue-name` (e.g. `bugfix/lazy-initialization-error`)
- **Refactoring**: `refactor/component-name` (e.g. `refactor/toast-context`)
- **Documentation**: `docs/doc-updates` (e.g. `docs/contributing-guidelines`)

---

## 📝 4. Commit Message Guidelines

Use clear commit prefixes following the Conventional Commits specification:

- `feat:` A new feature (e.g. `feat: add Quartz clustering configuration`)
- `fix:` A bug fix (e.g. `fix: resolve LazyInitializationException inside Job claim`)
- `docs:` Documentation changes (e.g. `docs: add Database ER diagram reference`)
- `style:` Visual edits that do not affect code logic (e.g. `style: fix alignment on dashboard stats cards`)
- `refactor:` Code changes that neither fix a bug nor add a feature (e.g. `refactor: clean up unused hook variables`)
- `test:` Adding missing tests or correcting existing tests (e.g. `test: add retry policy integration coverage`)

---

## 🔀 5. Pull Request Guidelines

Before submitting a Pull Request, verify your submission meets the following evaluation checklist:

1. **TypeScript compilation passes**: Verify that TypeScript emits no compilation or strict mode errors:
   ```bash
   cd dashboard
   npm run build
   ```
2. **Maven compilation succeeds**: Verify that the Java code compiles without warnings or test failures:
   ```bash
   ./mvnw clean compile
   ```
3. **Database migrations are sound**: Ensure any schema changes are appended as a new Flyway migration script under `src/main/resources/db/migration/` (e.g. `V4__Your_Change.sql`). Never modify existing migrations.
4. **Clean logs**: Verify that your code changes do not emit excessive log warnings or print stack traces directly to `System.out`. Use the Slf4j logger.
