FROM maven:3.9.16-eclipse-temurin-25-alpine
# Alpine использует apk, а не yum
RUN apk update && apk add --no-cache git

WORKDIR /app

# Копируем файлы проекта
COPY pom.xml .
COPY src ./src
# Копируем default.env (убедись, что файл рядом с Dockerfile)
COPY default.env /app/default.env

# Экспортим переменные из файла и запускаем сборку (sh: '.' поддерживается)
RUN set -o allexport && . /app/default.env && mvn clean package

CMD ["mvn", "install"]