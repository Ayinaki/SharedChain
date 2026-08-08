# Execution Blueprint

## Phase 1: Subagent Execution (Parallel)
- **build-config (Agent 1):** Audit build.gradle.kts, config.yml, and plugin.yml for Paper API and Java 25.0.3 alignment. Make safe minimal edits. Output `docs/config-reference.md` and `docs/audit-build.md`.
- **gameplay-arch (Agent 2):** Trace mechanics (chains, run management). Output `docs/feature-behavior-map.md`, `docs/system-overview.md` and `docs/audit-gameplay.md`. Add Javadoc to complex methods.
- **event-scheduler (Agent 3):** Audit events and tasks, specifically looking for heavy math on PlayerMoveEvent or main thread violations. Output `docs/event-flow.md` and `docs/audit-events.md`. Add Javadoc to complex methods.
- **data-audit (Agent 4):** Audit commands, world resets, lobby, and stats. Output `docs/codebase-map.md` and `docs/audit-data.md`. Add Javadoc to complex methods.
- **documentation (Agent 5):** Wait for the other 4 agents to finish. Read the codebase and their audit outputs to compile `docs/risk-register.md` and `docs/future-development-notes.md`. Clean up temporary audit files.

## Guidelines for all Subagents
- Strictly preserve behavior. Refactor only if critical for compilation/health.
- Perform deep codebase analysis and create the specified markdown files.
