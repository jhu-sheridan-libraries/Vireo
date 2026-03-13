# Implementation Plan: JHU Docker Image for AWS

## Overview

This implementation plan creates a production-ready Docker image of the Vireo ETD Management System optimized for Johns Hopkins University deployment in AWS container orchestration environments (ECS/Fargate). The implementation follows an incremental approach, building core infrastructure first, then adding AWS integrations, CI/CD automation, and comprehensive testing.

## Tasks

- [x] 1. Create Dockerfile.jhu with health checks and AWS optimizations
  - Create `Dockerfile.jhu` based on existing `Dockerfile` with multi-stage build (Maven + JRE stages)
  - Add HEALTHCHECK instruction: `wget --no-verbose --tries=1 --spider http://localhost:9000/` with interval=30s, timeout=10s, retries=3, start-period=60s
  - Add image labels for version, full Git SHA (40 chars), short Git SHA (12 chars), build timestamp, vendor (Johns Hopkins University)
  - Add JHU-specific label: `edu.jhu.vireo.build-label` following format `[Vireo-Head-SHA]-config-[Deployment-Head-SHA]` using 12-character short SHAs
  - Add label `org.opencontainers.image.revision` with full 40-character Vireo Git SHA
  - Add label `edu.jhu.vireo.config-sha` with full 40-character deployment config Git SHA
  - Configure graceful shutdown timeout (30s stop timeout)
  - Set NODE_ENV=production in Maven stage
  - Use production Maven profile: `-Pproduction -Dmaven.test.skip=true`
  - Install wget in JRE stage for health check: `apk add --update --no-cache wget`
  - Maintain non-root user (UID 1000) and expose port 9000
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 9.4, 9.5, 9.6, 9.7, 9.8_

- [ ] 2. Enhance docker-entrypoint.sh for environment variable validation
  - Modify `build/docker-entrypoint.sh` to validate required environment variables before templating
  - Add validation for AUTH_SERVICE_URL, LOCAL_AUTHENTICATION, STOMP_DEBUG, APP_PATH
  - Exit with code 1 and descriptive error message if required variables are missing
  - Log errors to STDERR for CloudWatch visibility
  - Ensure appConfig.js is written to $APP_PATH directory
  - _Requirements: 2.4, 7.4_


- [ ] 3. Configure Spring Boot for environment variable overrides
  - Verify `src/main/resources/application.yml` supports environment variable overrides for database configuration
  - Document environment variable mappings in comments: SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, SPRING_JPA_DATABASE_PLATFORM
  - Document security secret mappings: AUTH_SECURITY_JWT_SECRET, APP_SECURITY_SECRET
  - Document email configuration mappings: APP_EMAIL_HOST, APP_EMAIL_FROM
  - Document logging configuration: LOGGING_LEVEL_ORG_TDL
  - Configure graceful shutdown: `spring.lifecycle.timeout-per-shutdown-phase=25s`
  - Ensure logging outputs to STDOUT/STDERR (default Spring Boot behavior)
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.5, 8.1, 8.2, 8.3, 8.4_

- [ ] 4. Document image build and registry push process
  - [ ] 4.1 Create build script or document manual build commands
    - Document Maven build command: `mvn clean package -Pproduction -Dmaven.test.skip=true`
    - Document Docker build command with all build args (VERSION, VIREO_GIT_SHA_FULL, VIREO_GIT_SHA_SHORT, DEPLOYMENT_CONFIG_SHA_FULL, DEPLOYMENT_CONFIG_SHA_SHORT, BUILD_TIMESTAMP)
    - Document how to extract version from pom.xml
    - Document how to extract full Git SHA (40 chars) using `git rev-parse HEAD`
    - Document how to extract short Git SHA (12 chars) using `git rev-parse --short=12 HEAD`
    - Document image tagging strategy: latest, jhu-aws-{version}, jhu-aws-{short-sha}, jhu-aws-{timestamp}
    - Document GHCR push commands with authentication
    - Document ECR push commands with AWS authentication
    - Ensure no secrets are included in documentation (reference external secret management)
    - _Requirements: 9.1, 9.2, 9.3, 9.6, 11.8, 12.1, 12.2, 12.3, 12.4, 12.5, 7.7_

  - [ ]* 4.2 Write unit tests for build documentation
    - Test build documentation exists
    - Test documentation includes all required build args
    - Test documentation includes SHA extraction commands
    - Test documentation includes registry push commands for both GHCR and ECR
    - Test documentation does not contain hardcoded secrets
    - _Requirements: 11.8, 12.1, 12.2, 7.7_


- [ ] 5. Create ECS task definition template
  - Create `build/ecs-task-definition.json` template for AWS ECS deployment
  - Configure Fargate compatibility with 1 vCPU (1024 units) and 2GB memory (2048 MB)
  - Define container with GHCR image reference: `ghcr.io/jhu-sheridan-libraries/vireo:jhu-aws-{version}`
  - Add port 9000 mapping and health check configuration
  - Add environment variables: APP_PATH, AUTH_SERVICE_URL, LOCAL_AUTHENTICATION, STOMP_DEBUG
  - Add secrets placeholders supporting both Secrets Manager and Parameter Store ARNs
  - Document Secrets Manager format: `arn:aws:secretsmanager:region:account:secret:name`
  - Document Parameter Store format: `arn:aws:ssm:region:account:parameter/path`
  - Add example secrets for: SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, AUTH_SECURITY_JWT_SECRET, APP_SECURITY_SECRET
  - Configure CloudWatch Logs with awslogs driver
  - Add EFS volume configuration for /var/vireo mount
  - Set stopTimeout to 30 seconds for graceful shutdown
  - _Requirements: 5.1, 5.2, 5.4, 6.1, 6.2, 6.3, 7.1, 7.2, 7.5, 7.6, 8.5, 10.1, 10.2, 10.3, 10.4, 10.5, 12.4_

- [ ] 6. Write documentation (README)
  - [ ] 6.1 Create README.md in `.kiro/specs/jhu-docker-image-aws/`
    - Document purpose and differences from base Dockerfile
    - Document Docker build command with all build arguments (including SHA arguments)
    - Document all required environment variables with descriptions and examples
    - Document optional environment variables
    - Provide example docker run commands for local development
    - Provide example docker run commands for production deployment
    - Document differences between local development and production configuration
    - Document docker-compose-test.yml usage for local testing
    - Document manual image build and push process for GHCR and ECR
    - Document ECS deployment process with task definition
    - Document AWS Secrets Manager vs Parameter Store usage
    - Document image labeling convention: `[Vireo-Head-SHA]-config-[Deployment-Head-SHA]`
    - Document 12-character short SHA format for image tags
    - Include troubleshooting section for common errors
    - Include security best practices (no hardcoded secrets, non-root user)
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 7.6, 7.7, 9.6, 9.7, 12.1, 12.2_

  - [ ]* 6.2 Write unit tests for documentation completeness
    - Test README.md exists
    - Test README documents all build arguments including SHA arguments
    - Test README documents all required environment variables
    - Test README includes example commands for local and production
    - Test README documents Secrets Manager and Parameter Store
    - Test README documents SHA labeling convention
    - Test README documents manual registry push process
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.6, 11.7, 11.8, 12.1, 12.2_


- [ ] 7. Create docker-compose-test.yml for local testing
  - Create `docker-compose-test.yml` for local integration testing
  - Define vireo service using Dockerfile.jhu with build context
  - Define PostgreSQL service with test database configuration
  - Configure environment variables for vireo service (database connection, secrets, app config)
  - Configure volume mounts for /var/vireo and /vireo/logs
  - Configure network for service communication
  - Add health checks for both services
  - _Requirements: 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 6.4_

- [ ] 8. Checkpoint - Verify Docker image builds and runs locally
  - Build Docker image: `docker build -f Dockerfile.jhu -t vireo-jhu-aws:test .`
  - Run docker-compose-test.yml: `docker-compose -f docker-compose-test.yml up`
  - Verify application starts successfully and responds on port 9000
  - Verify health check passes: `docker inspect --format='{{.State.Health.Status}}' vireo`
  - Verify logs appear in STDOUT/STDERR
  - Verify environment variables override configuration
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 9. Write unit tests for Dockerfile validation
  - [ ] 9.1 Create test class `DockerfileValidationTest.java` in `src/test/java/org/tdl/vireo/docker/`
    - Test Dockerfile.jhu exists and is valid syntax
    - Test multi-stage build has exactly 2 FROM statements (Maven + JRE)
    - Test HEALTHCHECK instruction present with correct parameters
    - Test USER directive sets UID 1000 (non-root user)
    - Test EXPOSE 9000 directive present
    - Test Maven build uses -Pproduction and -Dmaven.test.skip=true
    - Test NODE_ENV=production in Maven stage
    - Test gettext package installed for envsubst
    - Test wget package installed for health check
    - Test image labels include: version, full Git SHA (org.opencontainers.image.revision), short Git SHA, timestamp
    - Test JHU-specific labels: edu.jhu.vireo.build-label, edu.jhu.vireo.config-sha
    - Test build-label follows format: `[Vireo-Head-SHA]-config-[Deployment-Head-SHA]` with 12-char SHAs
    - Test no hardcoded secrets in Dockerfile
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 9.6, 9.7, 9.8, 7.3, 7.7_

  - [ ]* 9.2 Create test class `EntrypointScriptTest.java` in `src/test/java/org/tdl/vireo/docker/`
    - Test script templates appConfig.js correctly with sample environment variables
    - Test script fails with exit code 1 when required variables missing
    - Test script creates appConfig.js in APP_PATH directory
    - Test script logs errors to STDERR
    - Test script does not log secrets to output
    - _Requirements: 2.4, 7.4, 7.7_


  - [ ]* 9.3 Create test class `ContainerRuntimeTest.java` in `src/test/java/org/tdl/vireo/docker/`
    - Test container starts successfully with minimal environment variables
    - Test container accepts volume mounts at /var/vireo and /vireo/logs
    - Test container runs as non-root user (UID 1000)
    - Test container exposes port 9000
    - Test health check endpoint returns 200 OK when application running
    - Test container responds to SIGTERM within 30 seconds
    - _Requirements: 1.3, 1.4, 3.1, 5.1, 5.2, 8.1, 8.2, 8.3, 8.5_

- [ ] 10. Write property-based tests for configuration override and logging
  - [ ] 10.1 Add jqwik dependency to pom.xml
    - Add jqwik dependency (net.jqwik:jqwik:1.7.4) with test scope
    - Add jqwik-spring dependency for Spring Boot integration
    - Configure surefire plugin to run jqwik tests
    - _Requirements: Design Section - Property-Based Testing Strategy_

  - [ ]* 10.2 Write property test for environment variable configuration override
    - **Property 1: Environment Variable Configuration Override**
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 6.1, 6.2, 6.3, 6.5, 7.1, 7.2**
    - Create test class `ConfigurationPropertyTest.java` in `src/test/java/org/tdl/vireo/docker/`
    - Generate random valid configuration values for Spring Boot properties
    - Set values as environment variables and start container
    - Verify application uses environment variable values instead of defaults
    - Test properties: spring.datasource.url, spring.datasource.username, spring.datasource.password, spring.jpa.database-platform, auth.security.jwt.secret, app.security.secret, app.email.host, app.email.from, logging.level.org.tdl
    - Run minimum 100 iterations per property
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 6.1, 6.2, 6.3, 6.5, 7.1, 7.2_

  - [ ]* 10.3 Write property test for application logging to standard streams
    - **Property 2: Application Logging to Standard Streams**
    - **Validates: Requirements 2.5**
    - Create test class `LoggingPropertyTest.java` in `src/test/java/org/tdl/vireo/docker/`
    - Generate log messages at various levels (INFO, DEBUG, WARN, ERROR)
    - Capture container STDOUT and STDERR output
    - Verify INFO/DEBUG messages appear on STDOUT
    - Verify WARN/ERROR messages appear on STDERR
    - Run minimum 100 iterations
    - _Requirements: 2.5_


- [ ] 11. Final checkpoint - Verify all components integrated
  - Build Docker image with Dockerfile.jhu
  - Run full test suite: `mvn clean test`
  - Verify all unit tests pass (Dockerfile validation, entrypoint script, workflow)
  - Verify all property tests pass (100 iterations each)
  - Test docker-compose-test.yml with PostgreSQL integration
  - Verify GitHub Actions workflow syntax is valid
  - Verify ECS task definition template is valid JSON
  - Verify README documentation is complete and accurate
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional testing tasks and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints (tasks 8 and 11) ensure incremental validation
- Property tests validate universal correctness properties with 100 iterations minimum
- Unit tests validate specific examples, edge cases, and configuration
- The implementation creates infrastructure files (Dockerfile, scripts, templates) before writing tests
- All Docker-related files are created in the repository root or build/ directory
- Test files follow the project structure convention: `src/test/java/org/tdl/vireo/docker/`
- The jqwik library is used for property-based testing in Java
- Image builds can be done manually or automated via CI/CD (CI/CD setup is a separate effort)
- ECS task definition is a template that requires customization for specific AWS environments
- Image labels follow OCI standards and JHU naming convention: `[Vireo-Head-SHA]-config-[Deployment-Head-SHA]`
- Short SHAs are 12 characters for readability in image tags
- Full SHAs (40 characters) are embedded in image labels for complete traceability
- Secrets must never be committed to the repository - use AWS Secrets Manager/Parameter Store
- Docker image runs as non-root user (UID 1000) for security
- Same Dockerfile works for both local development and production deployment (configuration via environment variables)
- GHCR and ECR are both supported as container registries
