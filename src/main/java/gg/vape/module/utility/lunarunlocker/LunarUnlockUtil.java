package gg.vape.module.utility.lunarunlocker;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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

    private static Boolean lunarRuntime = null;

    private LunarUnlockUtil() {
    }

    public static UnlockResult unlockAll() {
        if (!isAvailable()) {
            return UnlockResult.failure("");
        }

        Object lunarInstance = findLunarClientSingleton();
        if (lunarInstance == null) {
            return UnlockResult.failure("Could not find Lunar instance. Try again in a world.");
        }

        List<String> unlocked = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        // Try cosmetics v2 with artistTools, fallback to v1 without artistTools
        if (applyUnlock(lunarInstance, COSMETIC_LOGIN_V2, true, true)) {
            unlocked.add("cosmetics (v2)");
        } else if (applyUnlock(lunarInstance, COSMETIC_LOGIN_V1, true, false)) {
            unlocked.add("cosmetics (v1)");
        } else {
            failed.add("cosmetics");
        }

        // Unlock emotes
        if (applyUnlock(lunarInstance, EMOTE_LOGIN, true, false)) {
            unlocked.add("emotes");
        } else {
            failed.add("emotes");
        }

        // Unlock badges
        if (applyUnlock(lunarInstance, BADGE_LOGIN, true, false)) {
            unlocked.add("badges");
        } else {
            failed.add("badges");
        }

        // Unlock sprays
        if (applyUnlock(lunarInstance, SPRAY_LOGIN, true, false)) {
            unlocked.add("sprays");
        } else {
            failed.add("sprays");
        }

        if (unlocked.isEmpty()) {
            return UnlockResult.failure("Failed to unlock: " + String.join(", ", failed));
        }

        String message = "Unlocked: " + String.join(", ", unlocked);
        if (!failed.isEmpty()) {
            message += " (failed: " + String.join(", ", failed) + ")";
        }
        return UnlockResult.success(message);
    }

    public static boolean isAvailable() {
        if (lunarRuntime == null) {
            lunarRuntime = checkLunarRuntime();
        }
        return lunarRuntime;
    }

    private static boolean checkLunarRuntime() {
        ClassLoader[] loaders = getClassLoaders();
        for (ClassLoader loader : loaders) {
            try {
                Class.forName("com.lunarclient.bukkitapi.LunarClientAPI", false, loader);
                return true;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return false;
    }

    private static Object findLunarClientSingleton() {
        try {
            ClassLoader[] loaders = getClassLoaders();
            for (ClassLoader loader : loaders) {
                try {
                    Class<?> bridgeClass = Class.forName("lunar.LunarClient", false, loader);
                    Method[] methods = bridgeClass.getMethods();
                    for (Method method : methods) {
                        if (method.getParameterCount() == 0
                            && !method.getReturnType().equals(Void.TYPE)
                            && method.getName().length() <= 3) {
                            Object instance = method.invoke(null);
                            if (instance != null) {
                                return instance;
                            }
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static ClassLoader[] getClassLoaders() {
        List<ClassLoader> loaders = new ArrayList<>();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            loaders.add(contextLoader);
        }
        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        if (systemLoader != null) {
            loaders.add(systemLoader);
        }
        return loaders.toArray(new ClassLoader[0]);
    }

    private static Class<?> resolveClass(String className, ClassLoader loader) throws ClassNotFoundException {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            ClassLoader[] loaders = getClassLoaders();
            for (ClassLoader cl : loaders) {
                try {
                    return Class.forName(className, false, cl);
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
        try {
            Method[] methods = lunarInstance.getClass().getMethods();
            for (Method method : methods) {
                if (method.getParameterCount() == 0 && !method.getReturnType().equals(Void.TYPE)) {
                    Object managerCandidate = method.invoke(lunarInstance);
                    if (managerCandidate != null) {
                        if (invokeHandlerOnTarget(managerCandidate, responseClass, loginResponse)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean invokeLoginHandler(Object lunarInstance, Class<?> responseClass, Object loginResponse) {
        try {
            Method[] methods = lunarInstance.getClass().getMethods();
            for (Method method : methods) {
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(responseClass)) {
                    method.invoke(lunarInstance, loginResponse);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean invokeHandlerOnTarget(Object target, Class<?> responseClass, Object loginResponse) {
        try {
            Method[] methods = target.getClass().getMethods();
            for (Method method : methods) {
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(responseClass)) {
                    method.invoke(target, loginResponse);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Object buildLoginResponse(Class<?> responseClass, boolean premium, boolean artistTools) throws Exception {
        // Call newBuilder() static method to get a builder
        Method newBuilder = responseClass.getMethod("newBuilder");
        Object builder = newBuilder.invoke(null);

        // Set all flags using setBoolean helper
        setBoolean(builder, "setHasAllCosmeticsFlag", premium);
        setBoolean(builder, "setHasAllEmotesFlag", premium);
        setBoolean(builder, "setHasAllBadgesFlag", premium);
        setBoolean(builder, "setHasAllSpraysFlag", premium);
        setBoolean(builder, "setArtistTools", artistTools);

        // For cosmetics v2, add a default outfit
        if (COSMETIC_LOGIN_V2.equals(responseClass.getName())) {
            addDefaultOutfit(builder);
        }

        // Call build() to create the final response
        Method build = builder.getClass().getMethod("build");
        return build.invoke(builder);
    }

    private static void addDefaultOutfit(Object builder) {
        try {
            // Load Outfit class
            Class<?> outfitClass = Class.forName("com.lunarclient.websocket.cosmetic.v2.Outfit");
            Method newBuilder = outfitClass.getMethod("newBuilder");
            Object outfitBuilder = newBuilder.invoke(null);

            // Set outfit name
            Method setName = outfitBuilder.getClass().getMethod("setName", String.class);
            setName.invoke(outfitBuilder, "Infinite Yield");

            // Set favorite
            Method setFavorite = outfitBuilder.getClass().getMethod("setFavorite", boolean.class);
            setFavorite.invoke(outfitBuilder, true);

            // Build outfit
            Method buildOutfit = outfitBuilder.getClass().getMethod("build");
            Object outfit = buildOutfit.invoke(outfitBuilder);

            // Load OutfitTree class
            Class<?> outfitTreeClass = Class.forName("com.lunarclient.websocket.cosmetic.v2.OutfitTree");
            Method newTreeBuilder = outfitTreeClass.getMethod("newBuilder");
            Object treeBuilder = newTreeBuilder.invoke(null);

            // Add outfit to tree
            Method addOutfits = treeBuilder.getClass().getMethod("addOutfits", outfitClass);
            addOutfits.invoke(treeBuilder, outfit);

            // Get outfit ID
            Method getId = outfit.getClass().getMethod("getId");
            Object outfitId = getId.invoke(outfit);

            // Set default outfit ID
            Method setDefaultOutfitId = treeBuilder.getClass().getMethod("setDefaultOutfitId", String.class);
            setDefaultOutfitId.invoke(treeBuilder, outfitId);

            // Build tree
            Method buildTree = treeBuilder.getClass().getMethod("build");
            Object outfitTree = buildTree.invoke(treeBuilder);

            // Set outfit tree on the builder
            Method setOutfitTree = builder.getClass().getMethod("setOutfitTree", outfitTreeClass);
            setOutfitTree.invoke(builder, outfitTree);
        } catch (Exception ignored) {
            // Silently fail if outfit setup doesn't work
        }
    }

    private static void setBoolean(Object target, String methodName, boolean value) {
        try {
            Method method = target.getClass().getMethod(methodName, boolean.class);
            method.invoke(target, value);
        } catch (Exception ignored) {
            // Method might not exist in all versions
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
