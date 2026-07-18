FROM eclipse-temurin:25.0.3_9-jre-noble@sha256:2f1da100788559b397bcf48c736169ea5b070bde84e55f203bbee8e83d87a175 AS extractor

WORKDIR /builder
ARG JAR_FILE=build/libs/sentinel-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25.0.3_9-jre-noble@sha256:2f1da100788559b397bcf48c736169ea5b070bde84e55f203bbee8e83d87a175

WORKDIR /application
COPY --from=extractor --chown=10001:10001 /builder/extracted/dependencies/ ./
COPY --from=extractor --chown=10001:10001 /builder/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=10001:10001 /builder/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=10001:10001 /builder/extracted/application/ ./

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "application.jar"]
