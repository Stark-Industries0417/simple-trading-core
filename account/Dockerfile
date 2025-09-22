# Account Service
FROM --platform=linux/amd64 amazoncorretto:17-alpine

RUN addgroup -g 1000 trading && \
    adduser -D -s /bin/sh -u 1000 -G trading trading

RUN apk add --no-cache curl

WORKDIR /app

COPY --chown=trading:trading build/libs/account-*.jar app.jar

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Dspring.profiles.active=docker"
ENV TZ=Asia/Seoul

USER trading

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]