FROM openjdk:21-ea-jdk-oracle
COPY target/worker-2.0.0.jar worker-2.0.0.jar
EXPOSE 8011
VOLUME [ "/logs" ]
ENV INSTANCE_ID=default
ENTRYPOINT ["java", "-Dlogging.file.name=/logs/worker-${INSTANCE_ID}.log", "-jar", "worker-2.0.0.jar"]
