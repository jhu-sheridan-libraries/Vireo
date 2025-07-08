# Vireo GitHub Actions Workflows

This directory contains GitHub Actions workflows for automating various operational tasks for the Vireo application. These workflows provide a consistent "command grammar" for managing the application deployment, data, and testing processes.

All workflows requiring interaction with the application environment run on `self-hosted` runners.

## Secrets

These workflows utilize the following repository or organization secrets:

*   `VIREO_HOSTNAME`: The hostname (or IP address) of the target server for deployment and operations (used in stage/prod environments).
*   `JHU_DEVOPS_KEY`: The SSH private key for the `jhu-devops` user to access the target server.
*   `DB_PASSWORD`: The password for the database user (potentially used in data operations, though may be handled differently per workflow).

## Workflows

### 1. `build_docker.yml`

*   **Purpose**: Builds WAR artifacts and Docker images for deployment.
*   **Trigger**: Manual (`workflow_dispatch`).
*   **Inputs**:
    *   `images` (choice: `vireo`, `vireo-dev`, default: `vireo-dev`): The Docker image to build.
    *   `environment` (environment: `stage`, `prod`, default: `stage`): Target environment.
*   **Runner**: `ubuntu-latest`
*   **Description**: 
    1.  Sets up Java 11 and caches Maven/Node dependencies.
    2.  Builds WAR file with production settings (`mvn clean package -DskipTests -Dproduction`).
    3.  Uploads WAR and libs/ as GitHub Actions artifacts with version-specific naming.
    4.  Builds and pushes Docker images to GHCR if build succeeds.

### 2. `deploy_docker.yml`

*   **Purpose**: Deploys WAR artifacts as systemd service to target environment.
*   **Trigger**: Manual (`workflow_dispatch`).
*   **Inputs**:
    *   `images` (choice: `vireo`, `vireo-dev`, default: `vireo-dev`): Docker image reference (legacy).
    *   `environment` (environment: `stage`, `prod`, default: `stage`): Target deployment environment.
*   **Secrets**: `VIREO_HOSTNAME`, `JHU_DEVOPS_KEY`, `GITHUB_TOKEN`, `DB_PASSWORD`, `VIREO_JWT`, `AUTH_CRYPTOSERVICE`.
*   **Runner**: `self-hosted`.
*   **Description**:
    1.  Gets project version and sets up GitHub CLI.
    2.  Syncs repository code to target host (`/opt/vireo/Vireo`).
    3.  Executes remote SSH commands:
        *   Stops existing systemd service (`systemctl stop vireo`).
        *   Updates configuration files (.env, application.yml).
        *   Downloads versioned WAR and dependencies from GitHub Actions artifacts.
        *   Installs systemd service file and reloads daemon.
        *   Starts service and verifies status (`systemctl start vireo`).

### 3. `docker_restart.yml`

*   **Purpose**: Restarts the Docker containers for the application in a specific environment.
*   **Trigger**: Likely manual (`workflow_dispatch`).
*   **Inputs**: May require an `environment` input (`stage`/`prod`).
*   **Secrets**: `VIREO_HOSTNAME`, `JHU_DEVOPS_KEY`.
*   **Runner**: `self-hosted`.
*   **Description**: Executes remote SSH commands (e.g., `docker compose restart` or `down`/`up`) on the target host.

### 4. `docker_stop.yml`

*   **Purpose**: Stops the Docker containers for the application in a specific environment.
*   **Trigger**: Likely manual (`workflow_dispatch`).
*   **Inputs**: May require an `environment` input (`stage`/`prod`).
*   **Secrets**: `VIREO_HOSTNAME`, `JHU_DEVOPS_KEY`.
*   **Runner**: `self-hosted`.
*   **Description**: Executes remote SSH commands (e.g., `docker compose down`) on the target host.

### 5. `dump_postgres.yml`

*   **Purpose**: Creates a database dump from the PostgreSQL instance associated with an environment.
*   **Trigger**: Likely manual (`workflow_dispatch`) or potentially scheduled.
*   **Inputs**: May require an `environment` input (`stage`/`prod`).
*   **Secrets**: `VIREO_HOSTNAME`, `JHU_DEVOPS_KEY`, potentially `DB_PASSWORD` or relies on container access.
*   **Runner**: `self-hosted`.
*   **Description**: Executes remote SSH commands to run `pg_dump` against the appropriate database (likely within the running container or directly if accessible) and stores the dump file (e.g., `/opt/vireo/vireo4.dump`).

### 6. `load_rds.yml`

*   **Purpose**: Loads data (likely a database dump) into an RDS instance. This might be separate from the main application deployment database load step in `deploy_docker.yml`.
*   **Trigger**: Likely manual (`workflow_dispatch`).
*   **Inputs**: May require `environment` or specific RDS connection details/dump file location.
*   **Secrets**: Database credentials (`DB_PASSWORD`?), potentially AWS credentials if interacting via AWS CLI/SDK.
*   **Runner**: `self-hosted` or `ubuntu-latest` depending on how RDS is accessed.
*   **Description**: Connects to the target RDS instance and uses `psql` or another tool to import data from a specified dump file.

### 7. `mvn_start.yml` (renamed to Manage Vireo Service)

*   **Purpose**: Manages the Vireo systemd service without full deployment.
*   **Trigger**: Manual (`workflow_dispatch`).
*   **Inputs**:
    *   `action` (choice: `start`, `stop`, `restart`, `status`, default: `restart`): Service management action.
    *   `environment` (environment: `stage`, `prod`, default: `stage`): Target environment.
*   **Secrets**: `VIREO_HOSTNAME`, `JHU_DEVOPS_KEY`.
*   **Runner**: `self-hosted`.
*   **Description**: Executes systemd service commands (`systemctl start/stop/restart/status vireo`) and displays service status.

### 8. `test.yml`

*   **Purpose**: Runs automated tests for the Vireo application.
*   **Trigger**: Likely on `push` to branches (e.g., `main`, `develop`) and `pull_request`.
*   **Runner**: Github Cloud
*   **Description**: Checks out the code, sets up the required environment (e.g., specific Java/Maven versions), and executes the test suite (e.g., `mvn test`).