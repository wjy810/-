FROM eclipse-temurin:21-jre-jammy

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       libreoffice-writer libreoffice-java-common \
       tesseract-ocr tesseract-ocr-chi-sim tesseract-ocr-eng \
       fonts-noto-cjk fontconfig curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home --home-dir /home/jobproof jobproof

WORKDIR /app
COPY target/jobproof-backend.jar /app/jobproof-backend.jar

USER 10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:ActiveProcessorCount=2", "-XX:InitialRAMPercentage=15", "-XX:MaxRAMPercentage=72", "-XX:MaxMetaspaceSize=192m", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/jobproof-backend.jar"]
