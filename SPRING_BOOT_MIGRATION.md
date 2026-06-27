# Spring Boot Migration

The project now includes a Spring Boot rewrite under `backend/src/main/java/com/behindthesmile/posting`.

Current migrated commands:

- `draft`
- `auto-create`
- `daily`
- `build-x-links`
- `publish-queued-threads`
- `publish-queued-x`

Run examples:

```powershell
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=draft"
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=auto-create"
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=daily --threads-per-run 1 --x-per-run 1 --minimum-ready 8"
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=publish-queued-threads --index 1"
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=publish-queued-x --index 1"
```

Notes:

- This environment does not have `java`, `mvn`, or `gradle` installed, so the Spring Boot app could not be compiled or executed here.
- The legacy Node implementation now lives in `legacy-node/` until the Java version is validated locally.
- Existing PowerShell automation scripts still target the Node CLI. Switch them only after local Java verification succeeds.
