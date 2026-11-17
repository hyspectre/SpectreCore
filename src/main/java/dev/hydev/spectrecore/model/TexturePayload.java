package dev.hydev.spectrecore.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java sanity checks for Minecraft GameProfile `textures` payloads. NO network fetches. */
public final class TexturePayload {
    private static final Pattern SKIN = Pattern.compile("\\\"SKIN\\\"\\s*:\\s*\\{");
    private static final Pattern URL = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"(https?://textures\\.minecraft\\.net/texture/[a-fA-F0-9]{32,128})\\\"");
    private static final Pattern ANY_URL = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private TexturePayload() { }
    public static String checked(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 16384) throw new IllegalArgumentException("Base64 vacío o demasiado grande");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded.trim()); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Base64 inválido", error); }
        if (bytes.length > 12000) throw new IllegalArgumentException("Textura demasiado grande");
        String json = new String(bytes, StandardCharsets.UTF_8);
        Matcher matcher = URL.matcher(json);
        if (!json.contains("\"textures\"") || !SKIN.matcher(json).find() || !matcher.find())
            throw new IllegalArgumentException("Formato de textura sin SKIN de textures.minecraft.net");
        Matcher urls = ANY_URL.matcher(json);
        while (urls.find()) {
            if (!urls.group(1).matches("https?://textures\\.minecraft\\.net/texture/[a-fA-F0-9]{32,128}"))
                throw new IllegalArgumentException("URL de textura no permitida");
        }
        if (json.indexOf('\u0000') >= 0) throw new IllegalArgumentException("Textura inválida");
        return encoded.trim();
    }
}
