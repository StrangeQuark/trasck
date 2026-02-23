# Trasck
**Trasck** is an open source alternative to Agile management tools like Jira and Rally.<br><br>
This repository provides a Spring Boot/JPA API and database for the project.<br>
For the front end of the Trasck project, see: [trasck-frontend](https://github.com/StrangeQuark/trasck-frontend)
<br><br><br>

## Features
- Ready-to-run Docker environment
- Postman collection for testing and exploration
  <br><br><br>

## Technology Stack
- Java 17+
- Spring Boot
- PostgreSQL
- Docker & Docker Compose
- JPA (Hibernate)
- JUnit 5
  <br><br><br>

## Getting Started

### Prerequisites
- Docker and Docker Compose installed
- Java 17+ (for development or test execution outside Docker)
  <br><br>

### Running the Application
Clone the repository and start the service using Docker Compose:

```
git clone https://github.com/StrangeQuark/trasck.git
cd trasck
docker-compose up --build
```
<br>

### Environment Variables
The `.env` file is required to provide necessary configuration such as encryption secrets and database credentials. Default values are provided in `.env` file so the application can run out-of-the-box for testing.

⚠️ **Warning**: Do not deploy this application to production without properly changing your environment variables. The provided `.env` is not safe to use past local deployments!
<br><br>

## API Documentation
A Postman collection is included in the root of the project:

- `trasck.postman_collection.json`
  <br><br>

## Testing
Unit tests are provided for all repository and service-layer logic.
<br><br>

## Deployment
This project includes a `Jenkinsfile` for use in CI/CD pipelines. Jenkins must be configured with:

- Docker support
- Secrets or environment variables for configuration
- Access to any relevant private repositories, if needed
  <br><br>

## License
This project is licensed under the GNU General Public License. See `LICENSE.md` for details.
<br><br>

## Contributing
Contributions are welcome! Feel free to open issues or submit pull requests.
