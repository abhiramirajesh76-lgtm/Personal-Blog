FROM eclipse-temurin:21-jdk

WORKDIR /app
COPY personalblog/ .

RUN javac -d out $(find src -name "*.java")

EXPOSE 3000

CMD ["java", "-cp", "out", "com.blog.Main"]
