# Vireo Production Deployment Guide for JHU

This document outlines the steps required to deploy the Vireo application to the Johns Hopkins University (JHU) production environment using the `deploy_vireo.yml` GitHub Actions workflow. This workflow automates the process of transferring the application code, updating configuration, and managing the Vireo systemd service on the target server.

**Prerequisites**

Before proceeding with the deployment, ensure the following prerequisites are met:

*   **Access to the Vireo GitHub repository:** You must have the necessary permissions to trigger GitHub Actions workflows in the Vireo repository.
*   **Secrets configured in GitHub:** The following secrets must be configured in the repository settings:
    *   `VIREO_HOSTNAME`: The hostname (or IP address) of the JHU production server.
    *   `JHU_DEVOPS_KEY`: The SSH private key for the `jhu-devops` user on the JHU production server.
    *   `GITHUB_TOKEN`: A GitHub personal access token with sufficient permissions to download artifacts from the repository.
    *   `DB_PASSWORD`: The password for the Vireo database user.
    *   `VIREO_JWT`: A secret key used to encrypt JWTs (JSON Web Tokens).
    *   `AUTH_CRYPTOSERVICE`: A secret key used to encrypt confidential database entries and registration tokens.
*   **Target server setup:** The JHU production server must be properly configured with:
    *   The `jhu-devops` user with SSH access enabled.
    *   The required software dependencies (e.g., Java, Maven, systemd).
    *   The necessary directory structure (e.g., `/opt/vireo/Vireo`).
    *   A `vireo.service` systemd service file.
    *   Environment files (`.env`, `application.yml`) in the `/opt/vireo/` directory.

**Deployment Steps**

1.  **Trigger the `deploy_vireo.yml` workflow:**
    *   Navigate to the "Actions" tab in the Vireo GitHub repository.
    *   Select the `deploy_vireo` workflow from the list.
    *   Click the "Run workflow" button.

2.  **Configure the workflow inputs:**
    *   `images`: Choose the appropriate Docker image.  For production deployments, this is typically `vireo`.
    *   `environment`: Set the environment to `prod`.  This ensures that the workflow uses the correct secrets and configuration for the JHU production environment.

3.  **Monitor the workflow execution:**
    *   The workflow will begin executing, and you can monitor its progress in the Actions tab.
    *   The workflow performs the following steps (as detailed in `.github/workflows/deploy_vireo.yml`):
        *   Gets the project version from the `pom.xml` file.
        *   Sets up the GitHub CLI.
        *   Uses `rsync` to copy the repository code to the `/opt/vireo/Vireo` directory on the target server.
        *   Executes remote SSH commands on the target server to:
            *   Stop the existing Vireo systemd service (`systemctl stop vireo`).
            *   Update the configuration files (`.env`, `application.yml`).
            *   Download the versioned WAR file and dependencies from GitHub Actions artifacts.
            *   Install the systemd service file and reload the systemd daemon (`systemctl daemon-reload`).
            *   Start the Vireo service (`systemctl start vireo`).
            *   Check the status of the Vireo service (`systemctl status vireo`).

4.  **Verify the deployment:**
    *   Once the workflow completes successfully, verify that the Vireo application is running correctly in the JHU production environment.
    *   Check the application logs for any errors.
    *   Test the application functionality to ensure that it is working as expected.

**Troubleshooting**

*   **Workflow fails:** If the workflow fails, examine the workflow logs for error messages. Check the following:
    *   Verify that the secrets are configured correctly in GitHub.
    *   Ensure that the `jhu-devops` user has SSH access to the target server.
    *   Check that the required software dependencies are installed on the target server.
    *   Review the systemd service file (`vireo.service`) for any configuration errors.
*   **Application errors:** If the application encounters errors after deployment, check the application logs for details. Verify that the configuration files (`.env`, `application.yml`) are properly configured for the JHU production environment.

**See Also**

*   [Vireo GitHub Actions Workflows](.github/workflows/README.md) for an overview of the available workflows and their purposes.
*   [deploy_vireo.yml](.github/workflows/deploy_vireo.yml) for the specific steps performed by the deployment workflow.