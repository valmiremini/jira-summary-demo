# Start mit einem OpenJDK Image
FROM openjdk:11-jre-slim
VOLUME /tmp
COPY target/BewerbungsDemo-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
