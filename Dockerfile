# API + dashboard in one image. Build the inputs first (CI does this; see .github/workflows/image.yml):
#   ./gradlew :backend:installDist
#   (cd dashboard && npm ci && npm run build)
#   docker build -t metech-lunch .
# The Gradle build stays outside Docker because the project also contains the Android apps,
# which need the Android SDK just to configure.
FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 app
WORKDIR /app
COPY backend/build/install/backend/ /app/
COPY dashboard/dist/ /app/dashboard/

ENV PORT=8080 \
    DASHBOARD_DIR=/app/dashboard \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

USER app
EXPOSE 8080
ENTRYPOINT ["/app/bin/backend"]
