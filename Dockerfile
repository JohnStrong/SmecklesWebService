# Stage 1: Compile with full build toolchain (discarded after build)
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.11_10_1.12.11_3.3.7 AS builder
WORKDIR /app

# Deps layer — cached until build.sbt or project/ changes
COPY build.sbt build.sbt
COPY project/  project/
RUN sbt update

# Source layer — rebuilds on code changes only
COPY app/    app/
COPY conf/   conf/
RUN sbt stage

# Stage 2: Minimal runtime (JRE only, no sbt/JDK/source)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/universal/stage/ .
EXPOSE 9000
ENTRYPOINT ["bin/simpleshoppinglistapp", "-Dhttp.port=9000", "-J-Xmx256m"]
