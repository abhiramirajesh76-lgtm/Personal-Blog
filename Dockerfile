FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy the project files
COPY . .

# Compile the Java source files
RUN mkdir -p out && javac -d out $(find src -name "*.java")

# Expose the application port
EXPOSE 3000

# Run the application
CMD ["java", "-cp", "out", "com.blog.Main"]
