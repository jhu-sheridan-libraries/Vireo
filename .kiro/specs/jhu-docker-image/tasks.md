# Implementation Plan: JHU Docker Image for Local Testing

## Overview

This implementation plan creates a Docker image of the Vireo ETD Management System configured for Johns Hopkins University local development and testing. The implementation builds core Docker infrastructure, environment variable configuration, and local testing tooling.

## Tasks

- [x] 1. Create Dockerfile.jhu with health checks
  - Create `Dockerfile.jhu` based on existing `Dockerfile` with multi-stage build (Maven + JRE stages)
  - Add HEALTHCHECK instruction: `wget --no-verbose --tries=1 --spider http://localhost:9000/` with interval=30s, timeout=10s, retries=3, start-period=60s
  - Add OCI image labels: title, version, created, revision, source, vendor
  - Configure graceful shutdown timeout (30s stop timeout)
  - Set NODE_ENV=production in Maven stage
  - Use production Maven profile: `-Pproduction -Dmaven.test.skip=true`
  - Install wget in JRE stage for health check
  - Maintain non-root user (UID 1000) and expose port 9000
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 9.2, 9.3_

- [x] 2. Enhance docker-entrypoint.sh for environment variable validation
  - Modify `build/docker-entrypoint.sh` to validate required environment variables before templating
  - Add validation for AUTH_SERVICE_URL, LOCAL_AUTHENTICATION, STOMP_DEBUG, APP_PATH
  - Exit with code 1 and descriptive error message if required variables are missing
  - Log errors to STDERR for visibility in `docker logs` / `docker-compose logs`
  - Warn (but don't fail) if optional Spring Boot override variables are missing
  - Ensure appConfig.js is written to $APP_PATH directory
  - _Requirements: 2.4, 7.4_

- [x] 3. Configure Spring Boot for environment variable overrides
  - Verify `src/main/resources/application.yml` supports environment variable overrides for database configuration
  - Document environment variable mappings in comments: SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, SPRING_JPA_DATABASE_PLATFORM
  - Document security secret mappings: AUTH_SECURITY_JWT_SECRET, APP_SECURITY_SECRET
  - Document email configuration mappings: APP_EMAIL_HOST, APP_EMAIL_FROM
  - Document logging configuration: LOGGING_LEVEL_ORG_TDL
  - Configure graceful shutdown: `spring.lifecycle.timeout-per-shutdown-phase=25s`
  - Ensure logging outputs to STDOUT/STDERR (default Spring Boot behavior)
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 8.1, 8.2, 8.3, 8.4_

  - [x] 3.1 Configure environment variable injection for local testing
    - Update `.env` to include all documented override variables from `application.yml` (APP_SECURITY_SECRET, APP_EMAIL_HOST, APP_EMAIL_FROM, LOGGING_LEVEL_ORG_TDL)
    - Update `build/docker-entrypoint.sh` to optionally validate Spring Boot override variables (SPRING_DATASOURCE_URL, AUTH_SECURITY_JWT_SECRET, APP_SECURITY_SECRET) with warnings (not hard failures) when missing
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 6.1, 6.2, 6.3, 6.5, 7.1, 7.2_

- [x] 4. Document image build process
  - [x] 4.1 Create build documentation with local build commands
    - Document Maven build command: `mvn clean package -Pproduction -Dmaven.test.skip=true`
    - Document Docker build command with build args (VERSION, VIREO_GIT_SHA_FULL, VIREO_GIT_SHA_SHORT, BUILD_TIMESTAMP)
    - Document how to extract version from pom.xml
    - Document how to extract Git SHA using `git rev-parse HEAD` and `git rev-parse --short=12 HEAD`
    - Document local image tagging: `vireo-jhu:local`, `vireo-jhu:test`
    - Ensure no secrets are included in documentation
    - _Requirements: 9.1, 9.2, 9.3, 11.1, 11.2, 11.3_

- [x] 5. Write documentation (README)
  - [x] 5.1 Create README.md in `.kiro/specs/jhu-docker-image/`
    - Document purpose and differences from base Dockerfile
    - Document Docker build command with all build arguments
    - Document all required environment variables with descriptions and examples
    - Document optional environment variables
    - Provide example `docker run` commands for local testing with H2
    - Document `docker-compose.yml` usage for local stack testing with PostgreSQL
    - Document how to switch between H2 and PostgreSQL database backends
    - Include troubleshooting section for common local errors
    - Include security best practices (no hardcoded secrets, non-root user)
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_

- [x] 6. Create docker-compose.yml for local testing
  - Create `docker-compose.yml` for local integration testing
  - Define vireo service using Dockerfile.jhu with build context
  - Define PostgreSQL service with test database configuration and health check
  - Configure environment variables for vireo service (database connection, secrets, app config)
  - Configure named volume mounts for /var/vireo and /vireo/logs
  - Configure network for service communication
  - Add health checks for both services
  - Configure vireo service to depend on db being healthy
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 5.1, 5.2, 6.1, 6.2, 6.3_

- [x] 7. Checkpoint - Verify Docker image builds and runs locally
  - Build Docker image: `docker build -f Dockerfile.jhu -t vireo-jhu:test .`
  - Run docker-compose.yml: `docker-compose up --build`
  - Verify application starts successfully and responds on port 9000
  - Verify health check passes: `docker inspect --format='{{.State.Health.Status}}' vireo-test`
  - Verify logs appear in `docker-compose logs vireo`
  - Verify environment variables override configuration
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Final checkpoint - Ensure all tests pass
  - Run full test suite: `mvn clean test`
  - Test docker-compose.yml with PostgreSQL integration
  - Verify README documentation is complete and accurate
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Each task references specific requirements for traceability
- Checkpoints (tasks 7 and 8) ensure incremental validation
- All Docker-related files are in the repository root or `build/` directory
- Image is intended for local testing only — not for CI/CD or production deployment
- Docker image runs as non-root user (UID 1000) for security
- Secrets must never be committed to the repository — use environment variables or `.env` files (gitignored)
