# Start with a Gradle image for building the application
FROM gradle:8.7-jdk17 as builder
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . /home/gradle/src
RUN gradle bootJar

# The second stage of multi-stage build
# Use an Amazon Corretto image for running the application
FROM amazoncorretto:17
ENV TZ=Asia/Ho_Chi_Minh
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Ho_Chi_Minh"
COPY --from=builder /home/gradle/src/build/libs/metasearch-3.0.0.jar metasearch.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "metasearch.jar"]