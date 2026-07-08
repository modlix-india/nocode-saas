FROM openjdk:21-ea-jdk-oracle

COPY target/adzump-2.0.0.jar adzump-2.0.0.jar

EXPOSE 8012

VOLUME [ "/logs" ]

ENV INSTANCE_ID=default

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/adzump-${INSTANCE_ID}.log", "-jar", "adzump-2.0.0.jar"]
