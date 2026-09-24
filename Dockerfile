# =============================================================
# APEX full-stack (Java OOP backend + SQLite + static frontend)
# Build:  docker build -t apex-fullstack .
# Run  :  docker run -p 8080:8080 -v apex-data:/app/backend apex-fullstack
# =============================================================
FROM eclipse-temurin:11-jdk AS build
WORKDIR /app
COPY backend /app/backend
RUN cd /app/backend && mkdir -p out && javac -encoding UTF-8 -cp "lib/*" -d out $(find src -name "*.java")

FROM eclipse-temurin:11-jre
WORKDIR /app/backend
COPY --from=build /app/backend/out /app/backend/out
COPY --from=build /app/backend/lib /app/backend/lib
COPY --from=build /app/backend/seed.json /app/backend/seed.json
RUN mkdir -p /app/backend/uploads
COPY frontend /app/frontend
ENV PORT=3030 APEX_HOME=/app/backend
EXPOSE 3030
CMD ["java", "-cp", "/app/backend/out:/app/backend/lib/*", "com.apex.Main"]
