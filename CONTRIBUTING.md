# Working together

Use JDK 17+ and run `./mvnw verify` (`.\mvnw.cmd verify` on Windows). The wrapper downloads Maven on first use. Branch from current main using names such as `ayden/task-1-backend`, `aadi/task-1-frontend`, `ali/task-1-integration`, and `mujtaba/task-1-qa`. Open a pull request; have another teammate review before merging.

Keep shared records and LineMatcher stable while teammates develop. Discuss necessary interface changes and update ARCHITECTURE.md with them. UI and evaluation should consume the shared result rather than implementing competing matching algorithms. Every contributor includes meaningful checks and documents limitations.

Synthetic development fixtures are not benchmark data. Dataset gold labels must be verified independently, with provenance and permissions recorded. Do not upload credentials, personal data or the professor's slides. Do not mark tasks complete based solely on placeholder UI or fabricated accuracy numbers.
