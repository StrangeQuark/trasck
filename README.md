# Trasck
**Trasck** is an open source alternative to Agile management tools like Jira and Rally.<br><br>
This repository provides a Spring Boot/JPA API and database for the project.<br>
For the front end of the Trasck project, see: [trasck-frontend](https://github.com/StrangeQuark/trasck-frontend)
<br><br><br>

## Features
- Ready-to-run Docker environment
- Checked-in HTTP examples for testing and exploration
  <br><br><br>

## Technology Stack
- Java 21
- Spring Boot 4
- PostgreSQL
- Docker & Docker Compose
- JPA (Hibernate)
- JUnit 5
  <br><br><br>

## Getting Started

### Prerequisites
- Docker and Docker Compose installed
- Java 21 (for development or test execution outside Docker)
  <br><br>

### Running the Application
Clone the repository and start the service using Docker Compose:

```
git clone https://github.com/StrangeQuark/trasck.git
cd trasck
docker compose up --build
```
<br>

### Environment Variables
Copy `.env.example` to `.env` for local overrides. Environment variables provide configuration such as encryption secrets and database credentials.

⚠️ **Warning**: Do not deploy this application to production without replacing every development secret, password, local URL, and insecure cookie setting with production values.
<br><br>

## API Documentation
Runtime notes and HTTP examples are included in `docs/`:

- `docs/LOCAL_RUNTIME.md`
- `docs/http/trasck-api-examples.http`
  <br><br>

## Testing
Run the backend test suite with:

```
sh mvnw test
```
<br><br>

## Deployment
This project includes a `Jenkinsfile` for use in CI/CD pipelines. Jenkins must be configured with:

- Docker support
- Secrets or environment variables for configuration
- Access to any relevant private repositories, if needed
  <br><br>

## License
This project is licensed under the GNU General Public License. See `LICENSE` for details.
<br><br>

## Contributing
Contributions are welcome! Feel free to open issues or submit pull requests.
