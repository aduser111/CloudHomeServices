FROM openjdk:11-jdk
LABEL maintainer="研发组 "
RUN echo "Asia/Shanghai" > /etc/timezone
ARG PACKAGE_PATH=./target/jzo2o-customer.jar

ADD ${PACKAGE_PATH:-./} app.jar

EXPOSE 8080
EXPOSE 9999
ENTRYPOINT ["sh","-c","java  -jar $JAVA_OPTS app.jar"]