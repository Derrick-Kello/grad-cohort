# Build: docker build -t grps-api .   Run with DB_URL, JWT_PRIVATE_KEY, JWT_PUBLIC_KEY, STORAGE_SIGNING_KEY, WEB_ORIGIN set.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 --user-group grps
WORKDIR /app
COPY --from=build /src/target/grps-api-*.jar app.jar
RUN mkdir -p /app/data && chown grps:grps /app/data
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod STORAGE_DIR=/app/data/files
# Starts as root only to take ownership of /app/data (a mounted volume arrives root-owned), then runs the JVM as grps.
ENTRYPOINT ["sh", "-c", "chown grps:grps /app/data && exec setpriv --reuid=grps --regid=grps --init-groups java -XX:MaxRAMPercentage=75 -jar app.jar"]
