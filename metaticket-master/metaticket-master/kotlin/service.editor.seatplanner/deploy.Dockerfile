FROM amazoncorretto:18-alpine AS base

ARG VERSION=1.0.0
ARG PROJECT=service.editor.seatplanner
ARG PROJECT_PATH=/${PROJECT}
ENV CONF_PATH=${PROJECT_PATH}/conf
ENV LOGS_PATH=${PROJECT_PATH}/logs
ENV LIBS_PATH=${PROJECT_PATH}/libs

FROM base as builder

WORKDIR ${PROJECT_PATH}
# tool to unzip distribution file
RUN apk add unzip
COPY ./build/distributions ./
RUN unzip ./${PROJECT}-${VERSION}.zip && mv ./${PROJECT}-${VERSION}/lib ./libs

FROM base as apps

WORKDIR ${PROJECT_PATH}

COPY --from=builder ${PROJECT_PATH}/libs ./libs

CMD java -XX:+HeapDumpOnOutOfMemoryError -Xmx512M -cp ${CONF_PATH}/:${LIBS_PATH}/* metaticket.editor.seatplanner.Launcher