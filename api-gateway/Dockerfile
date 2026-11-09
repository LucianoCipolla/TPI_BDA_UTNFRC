# Imagen de una aplicación: copia el JAR que dejó build.sh (no compila nada)
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]