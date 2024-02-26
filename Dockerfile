FROM openjdk:18-jdk-slim
ENV PORT=8888
ENV ZMQPORT=8889
ENV FRED_API_KEY=""
ENV JAVA_OPTS=""
ENV APP=""

COPY . .
COPY applications/single-process-app/build/libs/*.jar /app.jar

EXPOSE ${PORT}
EXPOSE ${ZMQPORT}

ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${PORT} ${JAVA_OPTS} -jar "/app.jar"
