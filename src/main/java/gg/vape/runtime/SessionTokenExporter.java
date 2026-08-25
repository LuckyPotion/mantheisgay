package gg.vape.runtime;

import gg.vape.Vape;
import gg.vape.account.MinecraftSessionWrapper;
import gg.vape.mapping.Mapper;
import gg.vape.mapping.mappings.MSession;
import gg.vape.wrapper.impl.Minecraft;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;

/**
 * Injection-mode payload used by external launchers: instead of initializing the
 * client (license API, managers, module hooks) it locates the running Minecraft
 * session and copies its access token to the system clipboard.
 *
 * Only runs on this machine's own game process and never sends data anywhere.
 */
public final class SessionTokenExporter {
    private static final String TOKEN_MODE_PROPERTY = "vape421.tokenMode";

    private SessionTokenExporter() {
    }

    public static boolean isTokenModeEnabled() {
        return Boolean.parseBoolean(System.getProperty(TOKEN_MODE_PROPERTY, "true"));
    }

    public static void run(Vape vape) {
        NativeBridge.printLog("[SessionTokenExporter] token mode active; full client init skipped");
        String token = null;
        String username = null;
        String profileId = null;
        try {
            // Mapping load only: resolves classes/SRG names, touches no game bytecode.
            vape.loadMappings();
            MinecraftSessionWrapper session = Minecraft.Q$src$Lgg_vape_account_MinecraftSessionWrapper_$1ftnn3u();
            if (session != null && session.getObject() != null) {
                token = readToken(vape, session.getObject());
                try {
                    username = session.getUsername();
                }
                catch (Throwable ignored) {
                    // Username is informational; never fail the export over it.
                }
                try {
                    profileId = stringifyProfileId(session);
                }
                catch (Throwable ignored) {
                    // Profile id is informational; never fail the export over it.
                }
            }
        }
        catch (Throwable mappedPathFailure) {
            NativeBridge.printLog("[SessionTokenExporter] mapped path failed: " + mappedPathFailure);
        }
        if (token == null || token.isEmpty()) {
            // Mapped resolution failed (unknown remap, partial mappings, ...).
            // Fall back to a structure scan over the live Minecraft instance.
            Object sessionObject = locateSessionObject();
            if (sessionObject != null) {
                token = scanForToken(sessionObject);
            }
        }
        report(username, profileId, token);
    }

    private static void report(String username, String profileId, String token) {
        if (token == null || token.isEmpty()) {
            NativeBridge.printLog("[SessionTokenExporter] FAILED: no session token found"
                    + " (inject after the game session exists)");
            NativeBridge.sce("SessionTokenExporter: token not found");
            return;
        }
        boolean copied = copyToClipboard(token);
        NativeBridge.printLog("[SessionTokenExporter] username=" + username
                + " profileId=" + profileId
                + " token=" + token);
        NativeBridge.printLog(copied
                ? "[SessionTokenExporter] OK: access token copied to clipboard"
                : "[SessionTokenExporter] WARN: token found but clipboard copy failed");
        NativeBridge.sce(copied ? "OK SessionTokenExporter token copied" : "WARN clipboard unavailable");
    }

    private static String readToken(Vape vape, Object sessionObject) {
        Mapper mapper = vape.getMappings();
        if (mapper == null || mapper.hw == null) {
            return null;
        }
        return normalizeToken(mapper.hw.getToken(sessionObject));
    }

    private static String stringifyProfileId(MinecraftSessionWrapper session) {
        UUID profileId = session.getProfileId();
        return profileId == null ? null : profileId.toString();
    }

    /**
     * Structure-based fallback: resolve the Minecraft singleton without the
     * mapping pipeline and pull the token out of its session-shaped child.
     */
    private static Object locateSessionObject() {
        Class<?> minecraftClass = locateMinecraftClass();
        if (minecraftClass == null) {
            return null;
        }
        Object minecraft = locateStaticInstance(minecraftClass);
        if (minecraft == null) {
            return null;
        }
        for (Class<?> owner = minecraftClass; owner != null && owner != Object.class; owner = owner.getSuperclass()) {
            Field[] fields = owner.getDeclaredFields();
            for (int index = 0; index < fields.length; ++index) {
                Field field = fields[index];
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(minecraft);
                    if (value == null || !looksLikeSession(value.getClass())) {
                        continue;
                    }
                    return value;
                }
                catch (Throwable ignored) {
                    // Inaccessible or primitive member; keep scanning.
                }
            }
        }
        return null;
    }

    private static Class<?> locateMinecraftClass() {
        Class<?> resolved = NativeBridge.gvc("net/minecraft/client/Minecraft");
        if (resolved != null) {
            return resolved;
        }
        String[] candidates = {"net.minecraft.client.Minecraft", "net.minecraft.class_310"};
        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                NativeBridge.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()};
        for (int nameIndex = 0; nameIndex < candidates.length; ++nameIndex) {
            for (int loaderIndex = 0; loaderIndex < loaders.length; ++loaderIndex) {
                if (loaders[loaderIndex] == null) {
                    continue;
                }
                try {
                    return Class.forName(candidates[nameIndex], false, loaders[loaderIndex]);
                }
                catch (Throwable ignored) {
                    // Try the remaining loader/name combinations.
                }
            }
        }
        return null;
    }

    private static Object locateStaticInstance(Class<?> minecraftClass) {
        Field[] fields = minecraftClass.getDeclaredFields();
        for (int index = 0; index < fields.length; ++index) {
            Field field = fields[index];
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != minecraftClass) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object instance = field.get(null);
                if (instance != null) {
                    return instance;
                }
            }
            catch (Throwable ignored) {
                // Keep scanning other self-typed statics.
            }
        }
        return null;
    }

    private static boolean looksLikeSession(Class<?> candidate) {
        int stringFieldCount = 0;
        for (Class<?> owner = candidate; owner != null && owner != Object.class; owner = owner.getSuperclass()) {
            Field[] fields = owner.getDeclaredFields();
            for (int index = 0; index < fields.length; ++index) {
                Field field = fields[index];
                if (!Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                    ++stringFieldCount;
                }
            }
        }
        // Session/User carries at least username + token (+ profile id).
        return stringFieldCount >= 2;
    }

    private static String scanForToken(Object sessionObject) {
        String username = readStringMemberNamed(sessionObject, "username", "name", "field_74286_b", "f_92535_");
        String profileId = readStringMemberNamed(sessionObject, "playerID", "uuid", "field_148257_b", "f_92536_");
        String named = readStringMemberNamed(sessionObject,
                "token", "accessToken", "field_148258_c", "f_92537_");
        if (isPlausibleToken(named, username, profileId)) {
            return named.trim();
        }
        String jwtCandidate = null;
        String looseCandidate = null;
        for (Class<?> owner = sessionObject.getClass(); owner != null && owner != Object.class; owner = owner.getSuperclass()) {
            Field[] fields = owner.getDeclaredFields();
            for (int index = 0; index < fields.length; ++index) {
                Field field = fields[index];
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    String value = (String)field.get(sessionObject);
                    if (!isPlausibleToken(value, username, profileId)) {
                        continue;
                    }
                    String trimmed = value.trim();
                    if (trimmed.startsWith("eyJ")) {
                        jwtCandidate = trimmed;
                    }
                    else if (looseCandidate == null) {
                        looseCandidate = trimmed;
                    }
                }
                catch (Throwable ignored) {
                    // Unreadable member; keep scanning.
                }
            }
        }
        if (jwtCandidate != null) {
            return jwtCandidate;
        }
        return looseCandidate;
    }

    private static String readStringMemberNamed(Object target, String ... memberNames) {
        for (Class<?> owner = target.getClass(); owner != null && owner != Object.class; owner = owner.getSuperclass()) {
            Field[] fields = owner.getDeclaredFields();
            for (int index = 0; index < fields.length; ++index) {
                Field field = fields[index];
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                for (int nameIndex = 0; nameIndex < memberNames.length; ++nameIndex) {
                    if (!memberNames[nameIndex].equals(field.getName())) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        return (String)field.get(target);
                    }
                    catch (Throwable ignored) {
                        // Fall through to structural scanning.
                    }
                }
            }
        }
        return null;
    }

    private static boolean isPlausibleToken(String value, String username, String profileId) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 16 || trimmed.length() > 1024) {
            return false;
        }
        if (username != null && trimmed.equals(username.trim())) {
            return false;
        }
        if (profileId != null) {
            String compactProfileId = profileId.replace("-", "");
            if (trimmed.equalsIgnoreCase(profileId) || trimmed.equalsIgnoreCase(compactProfileId)) {
                return false;
            }
        }
        for (int index = 0; index < trimmed.length(); ++index) {
            char character = trimmed.charAt(index);
            boolean allowed = character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '.' || character == '_' || character == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeToken(String rawToken) {
        String trimmed = rawToken == null ? null : rawToken.trim();
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean copyToClipboard(String text) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
            return true;
        }
        catch (Throwable awtFailure) {
            NativeBridge.printLog("[SessionTokenExporter] AWT clipboard failed ("
                    + awtFailure + "), trying native bridge");
            try {
                NativeBridge.cpy(text);
                return true;
            }
            catch (Throwable nativeFailure) {
                NativeBridge.printLog("[SessionTokenExporter] native clipboard failed: " + nativeFailure);
                return false;
            }
        }
    }
}
