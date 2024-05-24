FROM eclipse-temurin:17.0.11_9-jre-alpine
VOLUME /tmp
ARG JAR_FILE=build/libs/gps-tools-backend-*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
