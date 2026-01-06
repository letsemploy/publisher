FROM docker.io/eclipse-temurin:25-jre-alpine
COPY ./target/app.jar /opt/app/
EXPOSE 8080
ENTRYPOINT ["java","-jar","/opt/app/app.jar"]
