import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class QuizServer {

    static class Room {
        List<String> players = new ArrayList<>();
        Set<String> departed = new HashSet<>();
        Map<String, Integer> scores = new ConcurrentHashMap<>();
        Map<String, Long> times = new ConcurrentHashMap<>();
        boolean started = false;
    }

    static Room room = new Room();

    public static void main(String[] args) throws Exception {

       int port = Integer.parselnt(System.getenv().getOrDefault("PORT","8080"));
       HttpServer server=HttpServer.create(new InetSocketAddress("0.0.0.",port),0);

        // PLAYER JOIN
        server.createContext("/api/join", exchange -> {

            Map<String, String> data = readForm(exchange);

            String name = data.getOrDefault("playerName", "").trim();

            boolean success = false;
            String message = "";

            synchronized (room) {

                if (name.isEmpty()) {
                    message = "Please enter a player name.";

                } else if (room.players.size() == 2) {
                    message = "The game is full.";

                } else if (!room.scores.isEmpty()
                        || !room.departed.isEmpty()) {
                    message = "The previous game has not finished resetting.";

                } else if (room.players.contains(name)) {
                    message = "This player name is already used.";

                } else {

                    room.players.add(name);
                    success = true;

                    if (room.players.size() == 2) {
                        room.started = true;
                    }
                }
            }

            String response =
                "{\"success\":" + success
                + ",\"playerCount\":" + room.players.size()
                + ",\"started\":" + room.started
                + ",\"message\":\"" + jsonEscape(message) + "\"}";

            sendJson(exchange, response);
        });

        // CHECK GAME STATUS
        server.createContext("/api/status", exchange -> {

            synchronized (room) {

                String response =
                    "{\"playerCount\":" + room.players.size()
                    + ",\"players\":" + toJson(room.players)
                    + ",\"started\":" + room.started + "}";

                sendJson(exchange, response);
            }
        });

        // SUBMIT PLAYER SCORE
        server.createContext("/api/finish", exchange -> {

            Map<String, String> data = readForm(exchange);

            String name = data.getOrDefault("playerName", "");

            int playerScore = Integer.parseInt(
                data.getOrDefault("score", "0")
            );

            long playerTime = Long.parseLong(
                data.getOrDefault("time", "0")
            );

            boolean success = false;

            synchronized (room) {

                if (room.started && room.players.contains(name)) {

                    room.scores.put(name, playerScore);
                    room.times.put(name, playerTime);

                    success = true;
                }
            }

            sendJson(exchange, "{\"success\":" + success + "}");
        });

        // GET FINAL RESULTS
        server.createContext("/api/results", exchange -> {

            synchronized (room) {

                if (room.scores.size() < 2) {
                    sendJson(exchange, "{\"ready\":false}");
                    return;
                }

                StringBuilder result = new StringBuilder();

                result.append("{\"ready\":true,\"players\":[");

                boolean first = true;

                for (String name : room.players) {

                    if (!first) result.append(",");
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

            String name = data.getOrDefault("playerName", "");

            synchronized (room) {

                if (room.players.contains(name)) {
                    room.departed.add(name);
                }

                // Reset only after both players have returned home
                if (room.players.size() == 2
                        && room.departed.size() == 2) {

                    room = new Room();
                }
            }

            sendJson(exchange, "{\"success\":true}");
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

            String type = "text/html";

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
        System.out.println("Open http://localhost:8080");
    }

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

    static String toJson(List<String> players) {

        StringJoiner result = new StringJoiner(",", "[", "]");

        for (String player : players) {
            result.add("\"" + jsonEscape(player) + "\"");
        }

        return result.toString();
    }

    static String jsonEscape(String value) {

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    static void sendJson(HttpExchange exchange, String response)
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