# publisher

publisher is a modular Spring Boot 4 web app for managing ojobpub JSON data across multiple tenants and domains.

## Features

- Java 25 + Spring Boot 4.0
- Thymeleaf + Thymeleaf Layout Dialect + Tabler UI
- HTMX-powered job search/filtering
- OIDC-only sign-in support (configurable, disabled by default for local bootstrap)
- Multi-tenant publishing model: users, tenants, domains, jobs, exports, and related reference data
- Public export endpoint per active domain export at `/public/domains/{domain-slug}/ojobpub.json`
- Flyway-managed schema with SQLite default and MariaDB/PostgreSQL profiles
- Multi-arch Docker build support and GitHub Actions for tests/builds

## Running locally

### Requirements

- Java 25
- Maven 3.9+

### Default SQLite profile

```bash
mvn spring-boot:run
```

### MariaDB profile

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mariadb
```

### PostgreSQL profile

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgresql
```

Adjust the datasource credentials in `src/main/resources/application-*.properties` or with environment variables before using external databases.

## Security

OIDC sign-in is the intended production mode. Enable it by setting:

```properties
publisher.security.enabled=true
spring.security.oauth2.client.registration.myprovider.client-id=...
spring.security.oauth2.client.registration.myprovider.client-secret=...
spring.security.oauth2.client.registration.myprovider.scope=openid,profile,email
spring.security.oauth2.client.provider.myprovider.issuer-uri=https://issuer.example.com
```

The default configuration leaves security disabled so the application can bootstrap without provider credentials.

## Public export and reverse proxy

Only one export can be active per domain. The active export is served as JSON from:

```text
/public/domains/{domain-slug}/ojobpub.json
```

Example nginx configuration:

```nginx
location = /public/domains/example-domain/ojobpub.json {
  proxy_pass http://publisher:8080/public/domains/example-domain/ojobpub.json;
  proxy_set_header Host $host;
}
```

## Testing

```bash
mvn test
```
