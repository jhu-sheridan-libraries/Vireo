# Technology Stack

## Build System

- **Maven 3.9.2** (primary build tool) - Maven 3.9.3+ is NOT compatible with the AngularJS frontend
- **NPM 8.0.0+** (frontend dependency management)
- **Node.js 16.0.0+** (frontend build tooling)

## Backend Stack

- **Java 11** (language version)
- **Spring Boot 2.x** (application framework)
  - Spring MVC (web layer)
  - Spring Data JPA (persistence)
  - Spring Security (authentication/authorization)
  - Spring WebSocket (real-time communication)
- **Hibernate** (ORM)
- **Thymeleaf** (server-side templating)
- **Weaver Framework** (custom TDL framework for auth, validation, email, reporting)

## Frontend Stack

- **AngularJS 1.x** (JavaScript framework)
- **@wvr/core 2.3.0-rc5** (Weaver frontend framework)
- **Bootstrap SASS** (UI framework)
- **TinyMCE 5.10.9** (rich text editor)
- **ng-table 3.1.0** (data tables)
- **WRO4J** (Web Resource Optimizer for asset bundling)

## Database Support

- **H2** (in-memory, default for development)
- **PostgreSQL** (recommended for production)
- **MySQL** (supported alternative)

## Key Libraries

- **Apache POI** (Excel file processing for ProQuest codes)
- **Apache Tika** (file type detection)
- **Apache Commons** (CSV, IO, FileUpload, HttpClient)
- **SWORD Common** (repository deposit protocol)
- **libphonenumber** (phone number validation)
- **human-name-parser** (name parsing)

## Common Commands

### Development

```bash
# Clean build
mvn clean package

# Run development server (port 9000)
mvn clean spring-boot:run

# Run with external configuration
mvn clean spring-boot:run -Dspring.config.location=file:/var/vireo/config/

# Run with external assets
mvn clean spring-boot:run -Dassets.uri=file:/var/vireo/

# Run as production mode
mvn clean spring-boot:run -Dproduction
```

### Testing

```bash
# Run server tests only
mvn clean test

# Run client tests only
npm run test

# Run both server and client tests
mvn clean test -Dclient
```

### Production Build

```bash
# Build production WAR and install package
mvn clean package -DskipTests -Dproduction -Dassets.uri=file:/opt/vireo/ -Dconfig.uri=file:/opt/vireo/config/
```

This produces:
- `target/vireo-4.3.2.war` (deployable WAR file)
- `target/vireo-4.3.2-install.zip` (installation package)

### Frontend Only

```bash
# Install dependencies
npm install

# Build frontend assets
npm run build

# Clean build artifacts
npm run clean
```

## Build Profiles

- **development** (default) - Symlinks assets, unoptimized builds
- **production** (`-Dproduction`) - Bundles assets into WAR, optimized builds
- **test-client** (`-DtestClient`) - Runs Karma tests for AngularJS

## Configuration

- **application.yml** - Main application configuration
- **pom.xml** - Maven build configuration
- **package.json** - NPM dependencies and scripts
- **.wvr/build-config.js** - Weaver build configuration

## Deployment Options

- Standalone JAR: `java -jar vireo-4.3.2.war`
- Tomcat WAR deployment
- Docker container (docker-compose.yml provided)

## Important Build Notes

- Building with `-Dproduction` modifies `src/main/resources/templates/index.html`
- External configuration directory requires trailing slash in `spring.config.location`
- Assets can be stored in classpath (default) or external filesystem
- WRO4J processes SASS files and bundles CSS/JS resources
