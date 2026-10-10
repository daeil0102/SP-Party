package net.teujaem.spparty.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PayloadCodec {
    private PayloadCodec() {}

    public static String encode(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        values.forEach((key, value) -> {
            if (result.length() > 0) result.append('&');
            result.append(key).append('=').append(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        });
        return result.toString();
    }

    public static Map<String, String> decode(String payload) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String field : payload.split("&")) {
            String[] pair = field.split("=", 2);
            if (pair.length != 2) continue;
            try { result.put(pair[0], new String(Base64.getUrlDecoder().decode(pair[1]), StandardCharsets.UTF_8)); }
            catch (IllegalArgumentException ignored) {}
        }
        return result;
    }
}
