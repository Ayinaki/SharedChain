# Execution Blueprint: Paper 26.2 Retargeting Pass

## Objective
Retarget the Chained Together Paper plugin cleanly to Paper 26.2 while preserving Microsoft OpenJDK 25.0.3 compatibility, threading safety, and all existing gameplay behavior.

## Scopes & Subagent Delegation

### Scope 1: Gradle & Metadata Retargeting
- **Target Files:** `build.gradle.kts`, `src/main/resources/plugin.yml`
- **Actions:** Update dependencies, runServer minecraftVersion, and `api-version` to Paper 26.2 (`io.papermc.paper:paper-api:26.2.build.111-stable`, `api-version: '26'`). Ensure Java 25 is maintained.

### Scope 2: API Compatibility & Breakage Fixes
- **Target Files:** Code in `src/main/java/me/ayinaki/ayinchallenge/`
- **Actions:** Audit for Paper 26.2 API drifts (e.g., changes to events, GameRules, or schedulers). Fix deprecations or removals locally to compile safely on 26.2. Do NOT refactor logic unrelated to compilation on 26.2. Maintain stable 20 TPS, async callbacks to GlobalRegionScheduler, and correct vector math.

### Scope 3: Documentation Correction
- **Target Files:** `docs/*`
- **Actions:** Scan `risk-register.md`, `codebase-map.md`, `config-reference.md`, and any walkthrough artifacts. Remove references to "26.2" and correctly document "Paper 26.2" as the current API target.

### Scope 4: Validation & Build Verification
- **Target Files:** Entire workspace
- **Actions:** Execute `./gradlew build`. Resolve any final compilation errors or missing dependencies caused by the 26.2 shift. Verify the output JAR builds cleanly under Java 25.
