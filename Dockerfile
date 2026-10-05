# AWS Public ECR mirrors Docker Hub official images (more reliable when hub.docker.com flakes)
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
COPY dgviz-gradle-plugin ./dgviz-gradle-plugin
# Required by settings.gradle.kts include() — directory must exist even when not packaging this module.
COPY dgviz-notifications ./dgviz-notifications
RUN chmod +x gradlew && ./gradlew :bootJar --no-daemon -x test

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-noble
WORKDIR /app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
COPY --from=build /workspace/build/libs/dgviz.jar /app/dgviz.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/dgviz.jar"]
