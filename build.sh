#!/bin/sh
# build.sh: compila todo en orden. Correr desde la raíz del repo.
set -e

# 1. El JAR común va al .m2: los microservicios lo buscan ahí
mvn -f campus-commons/pom.xml clean install

# 2. Cada aplicación arma su JAR ejecutable en target/ (lo copia su Dockerfile)
mvn -f ms-espacios/pom.xml clean package -DskipTests
mvn -f ms-reservas/pom.xml clean package -DskipTests
mvn -f api-gateway/pom.xml clean package -DskipTests

echo "Listo: los tres JAR están en */target/. Ahora: docker compose up --build"