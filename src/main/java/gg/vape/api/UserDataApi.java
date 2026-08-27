package gg.vape.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.config.Profile;
import gg.vape.runtime.LocalVapeStore;
import gg.vape.sync.RemoteProfileDataMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UserDataApi {
    private final String baseUrl;

    public CompletableFuture<ApiResponse<Boolean>> saveUserData(JsonObject userData) {
        return CompletableFuture.supplyAsync(() -> this.requestSaveUserData(null, userData));
    }

    private ApiResponse requestSaveUserData(String accessToken, JsonObject userData) {
        try {
            JsonObject stored = LocalVapeStore.readConfigObject();
            if (userData != null) {
                if (userData.has("friends")) {
                    stored.add("friends", userData.get("friends"));
                }
                JsonElement otherData = userData.get("otherData");
                if (otherData == null) {
                    otherData = userData.get("otherdata");
                }
                if (otherData != null) {
                    stored.add("otherData", otherData);
                    stored.add("otherdata", otherData);
                }
                if (userData.has("profiles")) {
                    stored.add("profiles", userData.get("profiles"));
                }
            }
            LocalVapeStore.writeConfigObject(stored);
            return ApiResponse.success(Boolean.TRUE);
        }
        catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public CompletableFuture<ApiResponse<RemoteProfileDataMap>> saveProfileData(JsonObject profileData) {
        return CompletableFuture.supplyAsync(() -> this.requestSaveProfileData(null, profileData));
    }

    private ApiResponse requestSaveProfileData(String accessToken, JsonObject profileData) {
        try {
            JsonObject stored = LocalVapeStore.readConfigObject();
            JsonObject profiles = stored.has("profiles") && stored.get("profiles").isJsonObject()
                    ? stored.getAsJsonObject("profiles")
                    : new JsonObject();
            if (profileData != null && profileData.has("updatedProfiles") && profileData.get("updatedProfiles").isJsonArray()) {
                JsonArray updated = profileData.getAsJsonArray("updatedProfiles");
                for (JsonElement element : updated) {
                    if (element == null || !element.isJsonObject()) {
                        continue;
                    }
                    JsonObject profileJson = element.getAsJsonObject();
                    Profile profile = new Profile("", "", true).loadJson(profileJson);
                    UUID profileId = profile.getOnlineId();
                    if (profileId == null) {
                        profileId = profile.getLocalId() != null ? profile.getLocalId() : UUID.randomUUID();
                        profile.setOnlineId(profileId);
                        profileJson.addProperty("profileId", profileId.toString());
                    }
                    if (profile.getLocalId() != null) {
                        profileJson.addProperty("uuid", profile.getLocalId().toString());
                    }
                    profiles.add(profileId.toString(), profileJson);
                }
            } else if (profileData != null && profileData.entrySet() != null && !profileData.has("updatedProfiles")) {
                for (Map.Entry<String, JsonElement> entry : profileData.entrySet()) {
                    if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                        continue;
                    }
                    profiles.add(entry.getKey(), entry.getValue());
                }
            }
            if (profileData != null && profileData.has("deletedProfiles") && profileData.get("deletedProfiles").isJsonArray()) {
                JsonArray deleted = profileData.getAsJsonArray("deletedProfiles");
                for (JsonElement element : deleted) {
                    if (element == null || element.isJsonNull()) {
                        continue;
                    }
                    profiles.remove(element.getAsString());
                }
            }
            stored.add("profiles", profiles);
            LocalVapeStore.writeConfigObject(stored);
            return ApiResponse.success(RemoteProfileDataMap.fromJson(profiles));
        }
        catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private ApiResponse requestUserData(String accessToken) {
        try {
            JsonObject stored = LocalVapeStore.readConfigObject();
            UserDataResponse response = UserDataResponse.fromJson(stored);
            if (response == null) {
                return ApiResponse.failure("empty config");
            }
            return ApiResponse.success(response);
        }
        catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private ApiResponse requestReservedProfileId(String accessToken) {
        return ApiResponse.success(UUID.randomUUID());
    }

    public CompletableFuture<ApiResponse<UserDataResponse>> getUserData() {
        return CompletableFuture.supplyAsync(() -> this.requestUserData(null));
    }

    private static UUID parseProfileId(JsonElement profileIdElement) {
        return UUID.fromString(profileIdElement.getAsString());
    }

    public CompletableFuture<ApiResponse<UUID>> reserveProfileId() {
        return CompletableFuture.supplyAsync(() -> this.requestReservedProfileId(null));
    }

    public UserDataApi(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
