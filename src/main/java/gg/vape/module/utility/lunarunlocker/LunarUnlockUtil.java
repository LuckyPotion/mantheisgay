package gg.vape.module.utility.lunarunlocker;

import gg.vape.wrapper.impl.Minecraft;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Lunar Client cosmetics unlocker utility
 * Ported from LunarUnlocker-1.0.meowtils
 */
public final class LunarUnlockUtil {
    private static final String COSMETIC_LOGIN_V2 = "com.lunarclient.websocket.cosmetic.v2.LoginResponse";
    private static final String COSMETIC_LOGIN_V1 = "com.lunarclient.websocket.cosmetic.v1.LoginResponse";
    private static final String EMOTE_LOGIN = "com.lunarclient.websocket.emote.v1.LoginResponse";
    private static final String BADGE_LOGIN = "com.lunarclient.websocket.badge.v1.LoginResponse";
    private static final String SPRAY_LOGIN = "com.lunarclient.websocket.spray.v1.LoginResponse";
    private static final String LUNAR_CLIENT_PACKAGE = "com/moonsworth/lunar/client/";
    private static final String LUNAR_TYPE_PREFIX = "com.moonsworth.lunar";

    private static Boolean lunarRuntime = null;

    private LunarUnlockUtil() {
    }

    public static UnlockResult unlockAll() {
        // Detection disabled: skip LunarClientAPI probe and always try unlock.
        // if (!isAvailable()) {
        //     return UnlockResult.failure("");
        // }

        Object lunarInstance = findLunarClientSingleton();
        if (lunarInstance == null) {
            return UnlockResult.failure("Could not find Lunar instance. Try again in a world.");
        }

        List<String> unlocked = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        if (applyUnlock(lunarInstance, COSMETIC_LOGIN_V2, true, true)) {
            unlocked.add("cosmetics (v2)");
        } else if (applyUnlock(lunarInstance, COSMETIC_LOGIN_V1, true, false)) {
            unlocked.add("cosmetics (v1)");
        } else {
            failed.add("cosmetics");
        }

        if (applyUnlock(lunarInstance, EMOTE_LOGIN, true, false)) {
            unlocked.add("emotes");
        } else {
            failed.add("emotes");
        }

        if (applyUnlock(lunarInstance, BADGE_LOGIN, true, false)) {
            unlocked.add("badges");
        } else {
            failed.add("badges");
        }

        if (applyUnlock(lunarInstance, SPRAY_LOGIN, true, false)) {
            unlocked.add("sprays");
        } else {
            failed.add("sprays");
        }

        if (unlocked.isEmpty()) {
            return UnlockResult.failure("Could not apply unlock (" + String.join(", ", failed) + ")");
        }

        String message = "Unlocked: " + String.join(", ", unlocked);
        if (!failed.isEmpty()) {
            message += " (failed: " + String.join(", ", failed) + ")";
        }
        return UnlockResult.success(message);
    }

    public static boolean isAvailable() {
        try {
            if (lunarRuntime == null) {
                lunarRuntime = detectLunarRuntime();
            }
            return Boolean.TRUE.equals(lunarRuntime);
        } catch (Throwable ignored) {
            lunarRuntime = Boolean.FALSE;
            return false;
        }
    }

    private static boolean detectLunarRuntime() {
        ClassLoader[] loaders = getClassLoaders();
        for (ClassLoader loader : loaders) {
            try {
                Class.forName(COSMETIC_LOGIN_V2, false, loader);
                return true;
            } catch (ClassNotFoundException ignored) {
                try {
                    Class.forName(COSMETIC_LOGIN_V1, false, loader);
                    return true;
                } catch (ClassNotFoundException ignoredAgain) {
                }
            }
        }
        return false;
    }

    private static Object findLunarClientSingleton() {
        return findSingletonFromLunarJar();
    }

    private static Object findSingletonFromLunarJar() {
        File lunarJar = findLunarJar();
        if (lunarJar == null || !lunarJar.isFile()) {
            return null;
        }
        for (String className : findTopLevelClientClasses(lunarJar)) {
            Object instance = singletonFromClass(className);
            if (instance != null) {
                return instance;
            }
        }
        return null;
    }

    private static File findLunarJar() {
        Class<?> marker = resolveClassOrNull(COSMETIC_LOGIN_V2);
        if (marker == null) {
            marker = resolveClassOrNull(COSMETIC_LOGIN_V1);
        }
        if (marker == null) {
            return null;
        }

        URL resource = null;
        ClassLoader loader = marker.getClassLoader();
        if (loader != null) {
            resource = loader.getResource(marker.getName().replace('.', '/') + ".class");
        }
        if (resource == null) {
            try {
                resource = marker.getProtectionDomain().getCodeSource().getLocation();
            } catch (Exception ignored) {
            }
        }
        return fileFromResourceUrl(resource);
    }

    private static File fileFromResourceUrl(URL url) {
        if (url == null) {
            return null;
        }
        try {
            URL fileUrl = url;
            if ("jar".equalsIgnoreCase(fileUrl.getProtocol())) {
                String file = fileUrl.getFile();
                int separator = file.indexOf('!');
                if (separator >= 0) {
                    file = file.substring(0, separator);
                }
                fileUrl = new URL(file);
            }
            File resolved = new File(fileUrl.toURI());
            return resolved.isFile() ? resolved : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> findTopLevelClientClasses(File jarFile) {
        List<String> classNames = new ArrayList<>();
        JarFile jar = null;
        try {
            jar = new JarFile(jarFile);
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith(LUNAR_CLIENT_PACKAGE) || !name.endsWith(".class")) {
                    continue;
                }
                String remainder = name.substring(LUNAR_CLIENT_PACKAGE.length(), name.length() - 6);
                if (remainder.isEmpty() || remainder.indexOf('/') >= 0 || remainder.indexOf('$') >= 0) {
                    continue;
                }
                classNames.add(name.substring(0, name.length() - 6).replace('/', '.'));
            }
        } catch (Exception ignored) {
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (Exception ignored) {
                }
            }
        }
        return classNames;
    }

    private static Object singletonFromClass(String className) {
        Class<?> type = resolveClassWithMarkerLoader(className);
        if (type == null) {
            return null;
        }
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                || method.getParameterCount() != 0
                || method.getReturnType() != type) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object instance = method.invoke(null);
                if (instance != null) {
                    return instance;
                }
            } catch (Exception ignored) {
            }
        }
        return readStaticSelfTypedField(type);
    }

    private static Object readStaticSelfTypedField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != type) {
                continue;
            }
            try {
                field.setAccessible(true);
                return field.get(null);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Class<?> resolveClassWithMarkerLoader(String className) {
        Class<?> marker = resolveClassOrNull(COSMETIC_LOGIN_V2);
        if (marker == null) {
            marker = resolveClassOrNull(COSMETIC_LOGIN_V1);
        }
        if (marker != null && marker.getClassLoader() != null) {
            try {
                return Class.forName(className, false, marker.getClassLoader());
            } catch (ClassNotFoundException ignored) {
            }
        }
        return resolveClassOrNull(className);
    }

    private static Class<?> resolveClassOrNull(String className) {
        try {
            return resolveClass(className, LunarUnlockUtil.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static ClassLoader[] getClassLoaders() {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            loaders.add(contextLoader);
        }
        ClassLoader selfLoader = LunarUnlockUtil.class.getClassLoader();
        if (selfLoader != null) {
            loaders.add(selfLoader);
        }
        try {
            ClassLoader mcLoader = Class.forName("net.minecraft.client.Minecraft").getClassLoader();
            if (mcLoader != null) {
                loaders.add(mcLoader);
            }
        } catch (Exception ignored) {
        }
        try {
            Object minecraft = Minecraft.i();
            if (minecraft != null) {
                ClassLoader mcLoader = minecraft.getClass().getClassLoader();
                if (mcLoader != null) {
                    loaders.add(mcLoader);
                }
            }
        } catch (Exception ignored) {
        }

        Set<ClassLoader> withParents = new LinkedHashSet<>(loaders);
        for (ClassLoader loader : loaders) {
            ClassLoader current = loader;
            while (current != null) {
                withParents.add(current);
                current = current.getParent();
            }
        }
        return withParents.toArray(new ClassLoader[0]);
    }

    private static Class<?> resolveClass(String className, ClassLoader loader) throws ClassNotFoundException {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            ClassLoader[] loaders = getClassLoaders();
            for (ClassLoader candidate : loaders) {
                try {
                    return Class.forName(className, false, candidate);
                } catch (ClassNotFoundException ignored) {
                }
            }
            throw new ClassNotFoundException(className);
        }
    }

    private static boolean applyUnlock(Object lunarInstance, String loginResponseClass, boolean premium, boolean artistTools) {
        try {
            Class<?> responseClass = resolveClass(loginResponseClass, lunarInstance.getClass().getClassLoader());
            Object loginResponse = buildLoginResponse(responseClass, premium, artistTools);
            if (invokeLoginHandler(lunarInstance, responseClass, loginResponse)) {
                return true;
            }
            return invokeCosmeticManagerDirect(lunarInstance, responseClass, loginResponse);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean invokeCosmeticManagerDirect(Object lunarInstance, Class<?> responseClass, Object loginResponse) {
        Method[] methods = lunarInstance.getClass().getMethods();
        for (Method method : methods) {
            if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) {
                continue;
            }
            try {
                Object managerCandidate = method.invoke(lunarInstance);
                if (managerCandidate != null && invokeHandlerOnTarget(managerCandidate, responseClass, loginResponse)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static boolean invokeLoginHandler(Object lunarInstance, Class<?> responseClass, Object loginResponse) {
        if (invokeHandlerOnTarget(lunarInstance, responseClass, loginResponse)) {
            return true;
        }
        Method[] methods = lunarInstance.getClass().getMethods();
        for (Method method : methods) {
            if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) {
                continue;
            }
            if (!method.getReturnType().getName().startsWith(LUNAR_TYPE_PREFIX)) {
                continue;
            }
            try {
                Object managerCandidate = method.invoke(lunarInstance);
                if (managerCandidate != null && invokeHandlerOnTarget(managerCandidate, responseClass, loginResponse)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static boolean invokeHandlerOnTarget(Object target, Class<?> responseClass, Object loginResponse) {
        for (Method method : getAllMethods(target.getClass())) {
            if (method.getParameterCount() != 1 || method.getReturnType() != Void.TYPE) {
                continue;
            }
            if (!method.getParameterTypes()[0].equals(responseClass)) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(target, loginResponse);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static List<Method> getAllMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                methods.add(method);
            }
            current = current.getSuperclass();
        }
        return methods;
    }

    private static Object buildLoginResponse(Class<?> responseClass, boolean premium, boolean artistTools) throws Exception {
        Method newBuilder = responseClass.getMethod("newBuilder");
        Object builder = newBuilder.invoke(null);

        setBoolean(builder, "setHasAllCosmeticsFlag", premium);
        setBoolean(builder, "setHasAllEmotesFlag", premium);
        setBoolean(builder, "setHasAllBadgesFlag", premium);
        setBoolean(builder, "setHasAllSpraysFlag", premium);
        setBoolean(builder, "setArtistTools", artistTools);

        if (COSMETIC_LOGIN_V2.equals(responseClass.getName())) {
            addDefaultOutfit(builder);
        }

        Method build = builder.getClass().getMethod("build");
        return build.invoke(builder);
    }

    private static void addDefaultOutfit(Object builder) {
        try {
            Class<?> outfitClass = resolveClass("com.lunarclient.websocket.cosmetic.v2.Outfit", builder.getClass().getClassLoader());
            Method newBuilder = outfitClass.getMethod("newBuilder");
            Object outfitBuilder = newBuilder.invoke(null);

            Method setName = outfitBuilder.getClass().getMethod("setName", String.class);
            setName.invoke(outfitBuilder, "Infinite Yield");

            Method setFavorite = outfitBuilder.getClass().getMethod("setFavorite", boolean.class);
            setFavorite.invoke(outfitBuilder, true);

            Method buildOutfit = outfitBuilder.getClass().getMethod("build");
            Object outfit = buildOutfit.invoke(outfitBuilder);

            Method addOutfits = builder.getClass().getMethod("addOutfits", outfitClass);
            addOutfits.invoke(builder, outfit);

            Class<?> outfitTreeClass = resolveClass("com.lunarclient.websocket.cosmetic.v2.OutfitTree", builder.getClass().getClassLoader());
            Method newTreeBuilder = outfitTreeClass.getMethod("newBuilder");
            Object treeBuilder = newTreeBuilder.invoke(null);

            Method getId = outfit.getClass().getMethod("getId");
            Object outfitId = getId.invoke(outfit);

            Method setDefaultOutfitId = treeBuilder.getClass().getMethod("setDefaultOutfitId", outfitId.getClass());
            setDefaultOutfitId.invoke(treeBuilder, outfitId);

            Method buildTree = treeBuilder.getClass().getMethod("build");
            Object outfitTree = buildTree.invoke(treeBuilder);

            Method setOutfitTree = builder.getClass().getMethod("setOutfitTree", outfitTreeClass);
            setOutfitTree.invoke(builder, outfitTree);
        } catch (Exception ignored) {
        }
    }

    private static void setBoolean(Object target, String methodName, boolean value) {
        try {
            Method method = target.getClass().getMethod(methodName, boolean.class);
            method.invoke(target, value);
        } catch (Exception ignored) {
        }
    }

    public static class UnlockResult {
        private final boolean success;
        private final String message;

        private UnlockResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static UnlockResult success(String message) {
            return new UnlockResult(true, message);
        }

        public static UnlockResult failure(String message) {
            return new UnlockResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
