FROM openjdk:21-ea-jdk-oracle

COPY target/config-1.1.0.jar config-1.1.0.jar

EXPOSE 8888

ARG profileName
ENV SPRING_PROFILE=$profileName

ENV INSTANCE_ID=default

VOLUME [ "/logs" ]

ENTRYPOINT ["java", "-Dspring.cloud.config.server.git.uri=${GIT_URL}", "-Dspring.cloud.config.server.git.username=${GIT_USERNAME}", "-Dspring.cloud.config.server.git.password=${GIT_PASSWORD}", "-Dlogging.file.name=/logs/config-${INSTANCE_ID}.log", "-jar", "config-1.1.0.jar"]