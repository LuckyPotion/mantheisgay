package gg.vape.runtime;

import com.google.gson.JsonObject;
import gg.vape.api.ApiHttpClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class LocalVapeStore {
    private static final Object LOCK = new Object();

    private LocalVapeStore() {
    }

    public static Path directory() {
        String programData = System.getenv("ProgramData");
        if (programData != null && !programData.isEmpty()) {
            return Paths.get(programData, ".vape");
        }
        String home = System.getenv("USERPROFILE");
        if (home == null || home.isEmpty()) {
            home = System.getProperty("user.home");
        }
        if (home == null || home.isEmpty()) {
            home = ".";
        }
        return Paths.get(home, ".vape");
    }

    public static Path idFile() {
        return directory().resolve("id");
    }

    public static Path configFile() {
        return directory().resolve("config.json");
    }

    public static Path settingsFile(String scope) {
        return directory().resolve("settings-" + scope + ".json");
    }

    public static void ensureDirectory() throws IOException {
        Files.createDirectories(directory());
    }

    public static String readId() {
        synchronized (LOCK) {
            return readText(idFile());
        }
    }

    public static void writeId(String id) throws IOException {
        synchronized (LOCK) {
            writeText(idFile(), id == null ? "" : id.trim());
        }
    }

    public static String readConfig() {
        synchronized (LOCK) {
            return readText(configFile());
        }
    }

    public static void writeConfig(String json) throws IOException {
        synchronized (LOCK) {
            writeText(configFile(), json == null ? "{}" : json);
        }
    }

    public static JsonObject readConfigObject() {
        String text = readConfig();
        if (text == null || text.isEmpty()) {
            return new JsonObject();
        }
        try {
            JsonObject parsed = ApiHttpClient.GSON.fromJson(text, JsonObject.class);
            return parsed == null ? new JsonObject() : parsed;
        }
        catch (Exception ignored) {
            return new JsonObject();
        }
    }

    public static void writeConfigObject(JsonObject object) throws IOException {
        writeConfig(ApiHttpClient.GSON.toJson(object == null ? new JsonObject() : object));
    }

    public static String readSettings(String scope) {
        synchronized (LOCK) {
            return readText(settingsFile(scope));
        }
    }

    public static void writeSettings(String scope, String json) throws IOException {
        synchronized (LOCK) {
            writeText(settingsFile(scope), json == null ? "{}" : json);
        }
    }

    private static String readText(Path path) {
        try {
            if (path == null || !Files.isRegularFile(path)) {
                return null;
            }
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? null : text;
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private static void writeText(Path path, String text) throws IOException {
        ensureDirectory();
        Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        Files.write(temp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException ignored) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
