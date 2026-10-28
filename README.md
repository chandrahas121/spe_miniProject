# Calculator REST API - CI/CD mini project

A small calculator REST API written in plain Java (no framework, just the JDK's built-in HTTP server),
built with Maven, with a full CI/CD pipeline (GitHub, Jenkins, Docker, Docker Compose).
Built for CSE 816 - Software Production Engineering.

## Requirements

- JDK 21
- Nothing else for Maven, the wrapper (`./mvnw`) downloads it

## Build, test, run

```bash
./mvnw test                  # run unit + integration tests
./mvnw clean package         # build target/calculator.jar
java -jar target/calculator.jar
```

The app listens on port **8081**. Set the `PORT` environment variable to change it.
Test reports (JUnit XML) end up in `target/surefire-reports/`.

## Run with Docker

Single container:

```bash
docker build -t calculator .
docker run -p 8081:8081 calculator
curl "localhost:8081/api/v1/add?a=2&b=3"
```

The Dockerfile is multi-stage (Maven + JDK to build, slim JRE alpine to run) and the container runs as a non-root user.

## Run with Docker Compose

Starts nginx in front of two calculator replicas:

```bash
docker compose up -d       # builds the image the first time
docker compose ps          # all three containers should show (healthy)
curl "localhost:8088/api/v1/add?a=2&b=3"
docker compose down
```

- nginx listens on **8088** and spreads requests over both replicas, the `X-Upstream` response header shows which one answered
- the calculator containers have a health check on `/actuator/health` and restart automatically
- `IMAGE_NAME` and `IMAGE_TAG` pick the image to run (Jenkins sets them), without them the image is built locally

## CI/CD pipeline (Jenkins)

The `Jenkinsfile` is a declarative pipeline. It starts on every push to GitHub (webhook, with Poll SCM as a fallback).

| Stage | What it does |
|-------|--------------|
| Checkout | pulls the latest code from GitHub |
| Build | `./mvnw clean package -DskipTests` |
| Test | `./mvnw test`, publishes the JUnit results, a failing test fails the build |
| Docker Build | builds `<dockerhub-user>/calculator:<build number>` and `latest` |
| Push | pushes both tags to Docker Hub |
| Deploy | `docker compose up -d` with that tag, then waits until `/actuator/health` answers `UP` |

After the run an email goes out saying whether the build succeeded or failed. Re-running the pipeline is safe,
Compose only recreates containers whose image changed, and builds never run at the same time.

### Jenkins setup

- the `jenkins` user must be in the `docker` group (`sudo usermod -aG docker jenkins`, then restart Jenkins)
- credentials, created in Jenkins under Manage Jenkins > Credentials, nothing secret is stored in the repo:
  - `dockerhub-creds`: username with password, the Docker Hub user and an access token
  - `notify-email`: secret text, the address that receives the build mails
- SMTP for the mails: Manage Jenkins > System > E-mail Notification
- job: New Item > Pipeline > Pipeline script from SCM > Git, this repo, branch `*/main`, script path `Jenkinsfile`
- trigger: the GitHub webhook points to `<jenkins-url>/github-webhook/`

## API

All endpoints are `GET` and take two numbers, `a` and `b`.

| Operation | Endpoint |
|-----------|----------|
| Add | `/api/v1/add?a=2&b=3` |
| Subtract | `/api/v1/subtract?a=2&b=3` |
| Multiply | `/api/v1/multiply?a=2&b=3` |
| Divide | `/api/v1/divide?a=6&b=3` |
| Health | `/actuator/health` |

```bash
$ curl "localhost:8081/api/v1/add?a=2&b=3"
{"operation":"add","a":2,"b":3,"result":5}

$ curl "localhost:8081/actuator/health"
{"status":"UP"}
```

Numbers are handled as `BigDecimal`, so `0.1 + 0.2` is `0.3`. Division is rounded to 16 significant digits.

### Errors

Missing parameters, non-numeric values and division by zero return HTTP 400 with a JSON body:

```bash
$ curl -i "localhost:8081/api/v1/divide?a=5&b=0"
HTTP/1.1 400 Bad Request
{"status":400,"error":"Bad Request","message":"Division by zero is not allowed"}
```

Unknown paths return 404 and anything other than `GET` returns 405.

## Project layout

```
src/main/java/com/chandrahas/calculator/
    Main                     starts the server
    CalculatorServer         HTTP routing, parameter parsing, JSON responses
    Calculator               the maths
src/test/java/...            JUnit 5 unit tests + HTTP integration tests
Jenkinsfile                  CI/CD pipeline
Dockerfile                   multi-stage image build
docker-compose.yml           nginx + two calculator replicas
nginx/default.conf           reverse proxy config
```
