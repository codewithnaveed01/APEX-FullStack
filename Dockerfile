# =============================================================
# APEX FullStack — Java OOP backend + PostgreSQL + static frontend
# Build:  docker build -t apex-fullstack .
# Run  :  docker compose up
# =============================================================
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY backend /app/backend
RUN cd /app/backend && mkdir -p out \
 && find src -name '*.java' -print0 | xargs -0 javac --release 17 -encoding UTF-8 -cp "lib/*" -d out

FROM eclipse-temurin:17-jre
WORKDIR /app/backend
COPY --from=build /app/backend/out /app/backend/out
COPY --from=build /app/backend/lib /app/backend/lib
COPY --from=build /app/backend/seed.json /app/backend/seed.json
COPY --from=build /app/backend/migrations /app/backend/migrations
RUN mkdir -p /app/backend/uploads
COPY frontend /app/frontend
ENV PORT=3030 \
    APEX_HOME=/app/backend \
    APEX_STATIC=/app/frontend
EXPOSE 3030
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s \
  CMD curl -fsS http://127.0.0.1:${PORT}/health || exit 1
CMD ["java", "-cp", "/app/backend/out:/app/backend/lib/*", "com.apex.Main"]
