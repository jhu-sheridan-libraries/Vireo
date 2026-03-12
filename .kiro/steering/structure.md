# Project Structure

## Root Directory Layout

```
vireo/
├── src/                          # Source code
│   ├── main/                     # Application code
│   │   ├── java/                 # Java backend
│   │   ├── resources/            # Configuration and data files
│   │   └── webapp/               # Frontend application
│   └── test/                     # Test code
├── dist/                         # Frontend build output
├── build/                        # Maven build output
├── target/                       # Maven compiled artifacts
├── node_modules/                 # NPM dependencies
├── logs/                         # Application logs
├── coverage/                     # Test coverage reports
├── .wvr/                         # Weaver build configuration
├── pom.xml                       # Maven configuration
└── package.json                  # NPM configuration
```

## Backend Structure (src/main/java/org/tdl/vireo/)

```
org.tdl.vireo/
├── Application.java              # Spring Boot entry point
├── ApplicationInitialization.java
├── ApplicationConstants.java
├── aspect/                       # AOP cross-cutting concerns
├── auth/                         # Authentication & authorization
│   ├── controller/               # Auth endpoints
│   ├── model/                    # Auth domain models
│   └── service/                  # Auth business logic
├── cli/                          # Command-line interface
├── config/                       # Spring configuration classes
│   ├── AppWebMvcConfig.java
│   ├── AppWebSecurityConfig.java
│   ├── AppWebSocketConfig.java
│   ├── VireoDatabaseConfig.java
│   └── VireoEmailConfig.java
├── controller/                   # REST API controllers
│   ├── SubmissionController.java
│   ├── OrganizationController.java
│   ├── UserController.java
│   └── [30+ domain controllers]
├── exception/                    # Custom exceptions
├── model/                        # Domain models (JPA entities)
│   ├── Submission.java
│   ├── Organization.java
│   ├── User.java
│   ├── WorkflowStep.java
│   ├── converter/                # JPA attribute converters
│   ├── depositor/                # Repository deposit implementations
│   ├── export/                   # Export format implementations
│   ├── formatter/                # Document formatters
│   ├── inheritance/              # Model inheritance patterns
│   ├── listener/                 # JPA entity listeners
│   ├── packager/                 # Package format implementations
│   ├── repo/                     # Spring Data repositories
│   ├── request/                  # Request DTOs
│   ├── response/                 # Response DTOs
│   └── validation/               # Validation annotations
├── service/                      # Business logic services
│   ├── SystemDataLoader.java
│   ├── DepositorService.java
│   ├── SubmissionEmailService.java
│   └── [15+ services]
├── ui/                           # UI-specific controllers
├── utility/                      # Helper utilities
│   ├── FileHelperUtility.java
│   ├── FormatterUtility.java
│   └── PackagerUtility.java
├── view/                         # JSON view definitions
└── wro/                          # Web Resource Optimizer config
```

## Frontend Structure (src/main/webapp/app/)

```
app/
├── app.js                        # AngularJS application bootstrap
├── components/                   # Reusable components
│   └── version/
├── config/                       # App configuration
│   ├── apiMapping.js             # API endpoint mappings
│   ├── routes.js                 # UI routing
│   └── runTime.js                # Runtime initialization
├── constants/                    # Application constants
│   ├── inputType.js
│   ├── submissionStates.js
│   └── submissionStatuses.js
├── controllers/                  # AngularJS controllers
│   ├── adminController.js
│   ├── submissionController.js
│   ├── settings/                 # Settings controllers
│   └── sidebar/                  # Sidebar controllers
├── directives/                   # Custom AngularJS directives
│   ├── fieldProfileDirective.js
│   ├── submissionDialogDirective.js
│   └── [30+ directives]
├── factories/                    # AngularJS factories
├── filters/                      # AngularJS filters
│   ├── displayFieldValue.js
│   └── [10+ filters]
├── model/                        # Frontend models
├── repo/                         # Data repositories
├── resources/                    # Static assets
│   ├── styles/                   # SASS/CSS files
│   ├── images/
│   └── fonts/
├── services/                     # AngularJS services
└── views/                        # HTML templates
    ├── admin/
    ├── submission/
    └── settings/
```

## Resources Structure (src/main/resources/)

```
resources/
├── application.yml               # Main configuration
├── banner.txt                    # Startup banner
├── favicon.ico
├── templates/                    # Thymeleaf templates
│   └── index.html
├── controlled_vocabularies/      # CV definitions (JSON)
├── degrees/                      # Degree definitions
├── degree_levels/                # Degree level definitions
├── document_types/               # Document type definitions
├── emails/                       # Email templates (.email files)
├── embargos/                     # Embargo definitions
├── filter_columns/               # Default filter columns
├── formats/                      # Export format templates
│   ├── dspace_mets.xml
│   ├── proquest_umi.xml
│   └── marc21.mrc
├── graduation_months/            # Graduation month definitions
├── input_types/                  # Input type definitions
├── languages/                    # Language definitions
├── organization/                 # Organization structure
├── organization_categories/      # Organization categories
├── proquest/                     # ProQuest code mappings (XLS)
├── settings/                     # System defaults
└── submission_statuses/          # Submission status definitions
```

## Test Structure (src/test/)

```
test/
├── java/org/tdl/vireo/          # Java unit/integration tests
│   ├── controller/               # Controller tests
│   ├── model/                    # Model tests
│   └── service/                  # Service tests
├── resources/
│   ├── application.yml           # Test configuration
│   └── logback.xml               # Test logging config
└── webapp/tests/                 # Frontend tests
    ├── core/                     # Core test utilities
    ├── mocks/                    # Mock data/services
    ├── unit/                     # Karma unit tests
    └── testSetup.js
```

## Key Architectural Patterns

### Backend
- **MVC Pattern**: Controllers handle HTTP, Services contain business logic, Repositories manage persistence
- **Repository Pattern**: Spring Data JPA repositories for data access
- **DTO Pattern**: Request/Response objects separate API contracts from domain models
- **Service Layer**: Business logic isolated in service classes
- **Inheritance**: Abstract base classes for shared model behavior (AbstractWorkflowStep, AbstractFieldProfile)

### Frontend
- **Component-Based**: Directives encapsulate reusable UI components
- **Repository Pattern**: Frontend repos mirror backend data access
- **MVC**: AngularJS controllers, views, and models
- **Service Layer**: Services handle API communication and shared logic

## Configuration Files

- **pom.xml**: Maven dependencies, build plugins, profiles
- **package.json**: NPM dependencies, build scripts
- **application.yml**: Spring Boot configuration (database, email, security)
- **.wvr/build-config.js**: Weaver frontend build configuration
- **karma.conf.js**: Karma test runner configuration
- **assembly.xml**: Maven assembly plugin configuration for packaging

## Important Conventions

- Java package structure follows domain-driven design
- Controllers are RESTful and return JSON responses
- All system data files prefixed with `SYSTEM_`
- Email templates use `.email` extension
- Frontend follows AngularJS 1.x style guide
- Tests mirror source structure
