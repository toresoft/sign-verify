# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Lingua
- Rispondere sempre in italiano, salvo diversa indicazione esplicita per il progetto.
- Commit git e commenti nel codice sempre in inglese.
- i piani e le specifiche devono essere in italiano

## Commit git
- Non aggiungere mai il footer Co-Authored-By: Claude nei messaggi di commit.

## Repository Status

This is a **freshly scaffolded Maven project** with no source code yet. The standard Maven directory layout exists (`src/main/java`, `src/main/resources`, `src/test/java`) but is empty. There is no README, no application entry point, and no committed dependencies.

The artifact coordinates are `org.toresoft:sign-verify-2:1.0-SNAPSHOT`. The project name suggests an intended scope around digital signature creation and verification, but no code is present to confirm the design.

## Build Configuration

- **Build tool:** Maven (no wrapper committed — use a system `mvn`)
- **Java source/target:** 26 (set in `pom.xml` via `maven.compiler.source` / `maven.compiler.target`)
- **Source encoding:** UTF-8
- **Dependencies:** none declared yet
- **Plugins:** none declared yet (relying on Maven defaults)

Java 26 is a recent release. Verify the local toolchain (`java -version`, `mvn -v`) supports it before adding code that uses preview or recent-release language features.

## Common Commands

```bash
# Compile
mvn compile

# Run tests (none exist yet — will be a no-op until tests are added)
mvn test

# Run a single test class once tests exist
mvn test -Dtest=FullyQualifiedTestClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName

# Package
mvn package

# Clean
mvn clean
```

No test framework dependency is declared yet — adding tests requires first adding JUnit (or another framework) to `pom.xml`.

## When Adding Code

Because the project is empty, the first meaningful additions will set conventions for everything that follows. Before writing code:

1. Confirm with the user the intended scope (which signature standards: PKCS#7 / CAdES / PAdES / XAdES / JWS / raw RSA-PSS / Ed25519, etc.) — the package layout and dependency choices follow from this.
2. Decide on the crypto provider (JCA default, BouncyCastle, etc.) and add it to `pom.xml` before writing implementation code that depends on it.
3. Add a test framework dependency (JUnit 5 is the conventional default for a new Java project) and the Surefire plugin version appropriate for Java 26 if the default does not work.
4. Establish the root package (likely `org.toresoft.signverify` based on the `groupId`) before scattering classes.

## IDE / Tooling Notes

- `.idea/` files are committed for IntelliJ project recognition; `.gitignore` excludes most generated IntelliJ files (`*.iml`, `modules.xml`, `libraries/`, etc.).
- `.mvn/` directory exists but is empty (no Maven Wrapper installed). If reproducible builds across machines matter, run `mvn wrapper:wrapper` to add `mvnw` / `mvnw.cmd`.
