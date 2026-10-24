# ===== Giai doan 1: build bang Maven =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Copy pom.xml truoc va tai thu vien -> lop nay duoc cache,
# lan sau chi sua code Java thi khong phai tai lai thu vien
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# ===== Giai doan 2: chi chua JRE + file jar =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/anomaly-detection.jar app.jar

# Detector khong mo cong nao: no chi CHU DONG goi ra Elasticsearch,
# khong ai goi vao no -> khong co EXPOSE.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]