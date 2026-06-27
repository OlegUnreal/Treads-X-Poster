$ErrorActionPreference = "Stop"

mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=auto-create"
