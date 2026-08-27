package gg.vape.api;

import gg.vape.runtime.LocalVapeStore;
import gg.vape.runtime.NativeBridge;

public class ApiAccessTokenProvider {
    private static String cachedAccessToken;
    private static String opaqueMarker;

    public static void setOpaqueMarker(String marker) {
        opaqueMarker = marker;
    }

    public static String getAccessToken() {
        if (cachedAccessToken == null) {
            ApiAccessTokenProvider.loadAccessToken();
        }
        return cachedAccessToken;
    }

    private static void loadAccessToken() {
        String localId = LocalVapeStore.readId();
        if (localId != null && !localId.isEmpty()) {
            cachedAccessToken = localId;
            return;
        }
        cachedAccessToken = NativeBridge.gat();
    }

    public static String getOpaqueMarker() {
        return opaqueMarker;
    }


    static {
        if (ApiAccessTokenProvider.getOpaqueMarker() == null) {
            ApiAccessTokenProvider.setOpaqueMarker("Sx5Qoc");
        }
    }
}

