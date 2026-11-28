# Working together

Use JDK 17+ and run `./mvnw verify` (`.\mvnw.cmd verify` on Windows). The wrapper downloads Maven on first use. Branch from current development using names such as `ayden/task-1-backend`, `aadi/task-1-frontend`, `ali/task-1-integration`, and `mujtaba/task-1-qa`. Open feature pull requests into development; have another teammate review before merging.

## Branch workflow

- `development` is the shared integration branch for ongoing work. Create each task branch from the latest development.
- Feature branches merge into development through pull requests with passing checks and teammate review.
- `main` holds reviewed, stable code. Promote development into main through a separate release pull request after review and successful checks.
- Do not push directly to main or merge an unreviewed draft. The initial backend foundation pull request targets development.

Typical flow: `task branch -> development -> reviewed release pull request -> main`.

Keep shared records and LineMatcher stable while teammates develop. Discuss necessary interface changes and update ARCHITECTURE.md with them. UI and evaluation should consume the shared result rather than implementing competing matching algorithms. Every contributor includes meaningful checks and documents limitations.

Synthetic development fixtures are not benchmark data. Dataset gold labels must be verified independently, with provenance and permissions recorded. Do not upload credentials, personal data or the professor's slides. Do not mark tasks complete based solely on placeholder UI or fabricated accuracy numbers.

## Before opening a pull request

Run `./mvnw spotless:apply` to format Java, then `./mvnw clean verify` to check formatting, run tests and package the application (use `.\mvnw.cmd` on Windows). CI performs the same checks on Windows and Linux and uploads the JAR and test reports. Use the PR template and link the Trello task. See [getting started](docs/GETTING_STARTED.md) for a complete walkthrough.

Dependency updates are proposed weekly into development through Dependabot after the configuration reaches the default branch; review and CI remain required. Both main and development require one approval and the aggregate test check.
