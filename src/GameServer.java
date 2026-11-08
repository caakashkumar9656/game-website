import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Number Guessing Game server. Pure Java (JDK 11+), no external dependencies.
 *
 * Run:  javac -d out src/GameServer.java && java -cp out GameServer
 * Open: http://localhost:8080
 */
public class GameServer {

    static final int MAX_NUMBER = 100;
    static final Path PUBLIC_DIR = Path.of("public");
    static final Path SCORES_FILE = Path.of("scores.txt");

    static final Map<String, Integer> games = new ConcurrentHashMap<>();
    static final Map<String, Integer> attempts = new ConcurrentHashMap<>();
    static final List<String[]> scores = Collections.synchronizedList(new ArrayList<>()); // {name, attempts}
    static final Random random = new Random();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0])
                : Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        loadScores();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/new", GameServer::newGame);
        server.createContext("/api/guess", GameServer::guess);
        server.createContext("/api/scores", GameServer::scoresHandler);
        server.createContext("/", GameServer::staticFiles);
        server.start();
        System.out.println("Game running at http://localhost:" + port);
    }

    // ---- API ----

    static void newGame(HttpExchange ex) throws IOException {
        String id = UUID.randomUUID().toString();
        games.put(id, random.nextInt(MAX_NUMBER) + 1);
        attempts.put(id, 0);
        send(ex, 200, "application/json", "{\"id\":\"" + id + "\",\"max\":" + MAX_NUMBER + "}");
    }

    static void guess(HttpExchange ex) throws IOException {
        Map<String, String> q = query(ex);
        String id = q.get("id");
        Integer secret = id == null ? null : games.get(id);
        if (secret == null) {
            send(ex, 404, "application/json", "{\"error\":\"Unknown game. Start a new one.\"}");
            return;
        }
        int n;
        try {
            n = Integer.parseInt(q.getOrDefault("n", ""));
        } catch (NumberFormatException e) {
            send(ex, 400, "application/json", "{\"error\":\"Enter a whole number.\"}");
            return;
        }
        if (n < 1 || n > MAX_NUMBER) {
            send(ex, 400, "application/json", "{\"error\":\"Pick a number between 1 and " + MAX_NUMBER + ".\"}");
            return;
        }
        int tries = attempts.merge(id, 1, Integer::sum);
        String result = n == secret ? "correct" : (n < secret ? "higher" : "lower");
        if (n == secret) {
            games.remove(id); // game over; keep attempts so the score can be submitted
        }
        send(ex, 200, "application/json", "{\"result\":\"" + result + "\",\"attempts\":" + tries + "}");
    }

    static void scoresHandler(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) {
            Map<String, String> q = query(ex);
            String id = q.get("id");
            Integer tries = id == null ? null : attempts.get(id);
            // only allow a score for a finished game (secret removed on win)
            if (tries == null || games.containsKey(id)) {
                send(ex, 400, "application/json", "{\"error\":\"Finish a game first.\"}");
                return;
            }
            attempts.remove(id);
            String name = q.getOrDefault("name", "Player").replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
            if (name.isEmpty()) name = "Player";
            if (name.length() > 16) name = name.substring(0, 16);
            scores.add(new String[]{name, String.valueOf(tries)});
            saveScores();
        }
        send(ex, 200, "application/json", scoresJson());
    }

    static String scoresJson() {
        List<String[]> copy;
        synchronized (scores) {
            copy = new ArrayList<>(scores);
        }
        copy.sort(Comparator.comparingInt(s -> Integer.parseInt(s[1])));
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(10, copy.size()); i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(copy.get(i)[0]).append("\",\"attempts\":").append(copy.get(i)[1]).append('}');
        }
        return sb.append(']').toString();
    }

    // ---- Score persistence ----

    static void loadScores() {
        try {
            if (Files.exists(SCORES_FILE)) {
                for (String line : Files.readAllLines(SCORES_FILE)) {
                    String[] parts = line.split("\t");
                    if (parts.length == 2) scores.add(parts);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load scores: " + e.getMessage());
        }
    }

    static void saveScores() {
        List<String> lines = new ArrayList<>();
        synchronized (scores) {
            for (String[] s : scores) lines.add(s[0] + "\t" + s[1]);
        }
        try {
            Files.write(SCORES_FILE, lines);
        } catch (IOException e) {
            System.err.println("Could not save scores: " + e.getMessage());
        }
    }

    // ---- Static files ----

    static void staticFiles(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = PUBLIC_DIR.resolve(path.substring(1)).normalize();
        if (!file.startsWith(PUBLIC_DIR) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain", "Not found");
            return;
        }
        String name = file.getFileName().toString();
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                : name.endsWith(".css") ? "text/css; charset=utf-8"
                : name.endsWith(".js") ? "application/javascript; charset=utf-8"
                : "application/octet-stream";
        byte[] body = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    // ---- Helpers ----

    static Map<String, String> query(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return map;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) continue;
            map.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
