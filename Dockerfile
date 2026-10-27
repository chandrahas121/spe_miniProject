# ---- build stage: Maven + JDK, only used to produce the jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# pom first, so the dependency layer is cached until the pom changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# tests already run in the Jenkins test stage
RUN mvn -B -q clean package -DskipTests


# ---- runtime stage: slim JRE, only the jar ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/target/calculator.jar app.jar

USER app

ENV PORT=8081
EXPOSE 8081

# exec form so java gets SIGTERM from docker stop
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
