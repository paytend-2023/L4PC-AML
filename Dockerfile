FROM public.ecr.aws/amazoncorretto/amazoncorretto:17
WORKDIR /app

ARG PROFILE=prod
ARG MODULE_NAME=l4pc-aml-screening
ARG COMMIT_ID=unknown

ENV SPRING_PROFILES_ACTIVE=${PROFILE}
ENV APP_COMMIT_ID=${COMMIT_ID}

COPY aml/target/${MODULE_NAME}-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
