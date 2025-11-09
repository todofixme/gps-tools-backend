FROM eclipse-temurin:21.0.9_10-jre-alpine
VOLUME /tmp
ARG JAR_FILE=build/libs/gps-tools-backend-*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
