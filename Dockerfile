FROM amazoncorretto:17-alpine as builder
WORKDIR /app

COPY ./build.gradle ./settings.gradle ./gradlew ./
COPY ./gradle ./gradle
#윈도우 시스템인 경우를 위해 dos2unix 실행
RUN dos2unix gradlew
#mac에서는 권한 설정이 필요
RUN chmod +x gradlew
#의존성 파일만 미리 빌드해서 캐시
RUN ./gradlew build -x test --parallel --continue > /dev/null 2>&1 || true

COPY ./src ./src
RUN ./gradlew build -x test --parallel

FROM amazoncorretto:17-alpine
COPY --from=builder /app/build/libs/bagulbagul-0.0.1-SNAPSHOT.jar ./
ENTRYPOINT ["java", "-jar", "bagulbagul-0.0.1-SNAPSHOT.jar"]