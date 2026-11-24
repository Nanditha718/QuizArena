FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY src/QuizServer.java .
COPY public ./public
RUN javac --add-modules jdk.httpserver
QuizServer.java

EXPOSE 8080

CMD
["java","--add-modules","jdk.httpserver","QuizServer"]