# AWS Public ECR mirrors Docker Hub official images
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY dgviz-notifications ./dgviz-notifications
# Root build files needed so settings.gradle can resolve the multi-module project.
# Plugin and main app sources are not required to build only notifications.
COPY dgviz-gradle-plugin ./dgviz-gradle-plugin
COPY src ./src
RUN chmod +x gradlew && ./gradlew :dgviz-notifications:bootJar --no-daemon -x test

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-noble
WORKDIR /app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
COPY --from=build /workspace/dgviz-notifications/build/libs/dgviz-notifications.jar /app/dgviz-notifications.jar
EXPOSE 8082
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/dgviz-notifications.jar"]
