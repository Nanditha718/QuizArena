
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

public class QuizServer {

    static class Room {
        List<String> players = new ArrayList<>();
        Set<String> departed = new HashSet<>();
        Map<String, Integer> scores = new HashMap<>();
        Map<String, Long> times = new HashMap<>();
        boolean started = false;
    }

    static final Object LOCK = new Object();
    static Room room = new Room();

    public static void main(String[] args) throws Exception {

        int port = Integer.parseInt(
            System.getenv().getOrDefault("PORT", "8080")
        );

        HttpServer server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", port), 0
        );

        // PLAYER JOIN
        server.createContext("/api/join", exchange -> {

            Map<String, String> data = readForm(exchange);
            String name = data.getOrDefault("playerName", "").trim();

            boolean success = false;
            String message = "";

            synchronized (LOCK) {

                if (name.isEmpty()) {
                    message = "Please enter a player name.";

                } else if (room.players.size() >= 2) {
                    message = "The game is full.";

                } else if (!room.scores.isEmpty()) {
                    message = "Previous game is still finishing.";

                } else if (room.players.contains(name)) {
                    message = "This player name is already used.";

                } else {
                    room.players.add(name);
                    success = true;

                    if (room.players.size() == 2) {
                        room.started = true;
                    }
                }

                sendJson(exchange,
                    "{\"success\":" + success
                    + ",\"playerCount\":" + room.players.size()
                    + ",\"started\":" + room.started
                    + ",\"message\":\"" + jsonEscape(message) + "\"}"
                );
            }
        });

        // CHECK GAME STATUS
        server.createContext("/api/status", exchange -> {

            synchronized (LOCK) {

                sendJson(exchange,
                    "{\"playerCount\":" + room.players.size()
                    + ",\"players\":" + toJson(room.players)
                    + ",\"started\":" + room.started + "}"
                );
            }
        });

        // SUBMIT PLAYER SCORE
        server.createContext("/api/finish", exchange -> {

            Map<String, String> data = readForm(exchange);

            String name = data.getOrDefault("playerName", "").trim();

            int playerScore = Integer.parseInt(
                data.getOrDefault("score", "0")
            );

            long playerTime = Long.parseLong(
                data.getOrDefault("time", "0")
            );

            boolean success = false;
            String message;

            synchronized (LOCK) {

                if (!room.started) {
                    message = "Game has not started.";

                } else if (!room.players.contains(name)) {
                    message = "Player not found.";

                } else {
                    room.scores.put(name, playerScore);
                    room.times.put(name, playerTime);

                    success = true;
                    message = "Score received.";
                }

                System.out.println(
                    "FINISH: " + name
                    + " | Scores received: " + room.scores.size()
                    + " | Players: " + room.players
                );

                sendJson(exchange,
                    "{\"success\":" + success
                    + ",\"message\":\"" + jsonEscape(message) + "\"}"
                );
            }
        });

        // GET FINAL RESULTS
        server.createContext("/api/results", exchange -> {

            synchronized (LOCK) {

                if (room.scores.size() < 2) {
                    sendJson(exchange, "{\"ready\":false}");
                    return;
                }

                StringBuilder result = new StringBuilder();

                result.append("{\"ready\":true,\"players\":[");

                boolean first = true;

                for (String name : room.players) {

                    if (!first) {
                        result.append(",");
                    }

                    first = false;

                    result.append("{\"name\":\"")
                        .append(jsonEscape(name))
                        .append("\",\"score\":")
                        .append(room.scores.get(name))
                        .append(",\"time\":")
                        .append(room.times.get(name))
                        .append("}");
                }

                result.append("]}");

                sendJson(exchange, result.toString());
            }
        });

        // PLAYER RETURNS HOME
        server.createContext("/api/leave", exchange -> {

            Map<String, String> data = readForm(exchange);
            String name = data.getOrDefault("playerName", "").trim();

            synchronized (LOCK) {

                if (room.players.contains(name)) {
                    room.departed.add(name);
                }

                if (room.players.size() == 2
                        && room.departed.size() == 2) {

                    System.out.println("Both players left. Resetting room.");
                    room = new Room();
                }

                sendJson(exchange, "{\"success\":true}");
            }
        });

        // SERVE WEBSITE FILES
        server.createContext("/", exchange -> {

            Path root = Paths.get("public")
                .toAbsolutePath().normalize();

            String path = exchange.getRequestURI().getPath();

            if (path.equals("/")) {
                path = "/index.html";
            }

            Path file = root.resolve(path.substring(1)).normalize();

            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            String type = "application/octet-stream";

            if (path.endsWith(".html")) type = "text/html";
            if (path.endsWith(".css")) type = "text/css";
            if (path.endsWith(".js")) type = "application/javascript";
            if (path.endsWith(".json")) type = "application/json";
            if (path.endsWith(".svg")) type = "image/svg+xml";
            if (path.endsWith(".png")) type = "image/png";

            byte[] content = Files.readAllBytes(file);

            exchange.getResponseHeaders().set(
                "Content-Type", type + "; charset=UTF-8"
            );

            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });

        server.start();

        System.out.println("Quiz Arena Server Started!");
        System.out.println("Server running on port " + port);
    }

    // READ FORM DATA
    static Map<String, String> readForm(HttpExchange exchange)
            throws IOException {

        String body = new String(
            exchange.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );

        Map<String, String> data = new HashMap<>();

        for (String item : body.split("&")) {

            String[] pair = item.split("=", 2);

            if (pair.length == 2) {
                data.put(
                    URLDecoder.decode(pair[0], "UTF-8"),
                    URLDecoder.decode(pair[1], "UTF-8")
                );
            }
        }

        return data;
    }

    // CONVERT PLAYERS TO JSON
    static String toJson(List<String> players) {

        StringJoiner result = new StringJoiner(",", "[", "]");

        for (String player : players) {
            result.add("\"" + jsonEscape(player) + "\"");
        }

        return result.toString();
    }

    // ESCAPE JSON TEXT
    static String jsonEscape(String value) {

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    // SEND JSON RESPONSE
    static void sendJson(
            HttpExchange exchange, String response)
            throws IOException {

        byte[] data = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=UTF-8"
        );

        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }
}
