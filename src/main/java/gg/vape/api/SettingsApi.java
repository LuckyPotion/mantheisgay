package gg.vape.api;

import gg.vape.config.RefreshableSettingsPayload;
import gg.vape.config.SettingsDataType;
import gg.vape.config.SettingsPayload;
import gg.vape.runtime.LocalVapeStore;

public class SettingsApi {
    private final String baseUrl;

    public <T> T saveSettings(SettingsDataType settingsDataType, SettingsPayload settingsPayload) throws Exception {
        if (settingsPayload instanceof RefreshableSettingsPayload) {
            ((RefreshableSettingsPayload)settingsPayload).refreshFromCurrentSettings();
        }
        String json = ApiHttpClient.GSON.toJson(settingsPayload);
        LocalVapeStore.writeSettings(settingsDataType.getScope().getRouteName(), json);
        return (T)settingsPayload;
    }

    public SettingsApi(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private static Exception preserveException(Exception error) {
        return error;
    }

    public <T> ApiResponse<T> loadSettings(SettingsDataType settingsDataType) throws Exception {
        String json = LocalVapeStore.readSettings(settingsDataType.getScope().getRouteName());
        if (json == null || json.isEmpty()) {
            return ApiResponse.failure("not found");
        }
        T payload = (T)ApiHttpClient.GSON.fromJson(json, settingsDataType.getPayloadClass());
        if (payload == null) {
            return ApiResponse.failure("empty settings");
        }
        return ApiResponse.success(payload);
    }
}
