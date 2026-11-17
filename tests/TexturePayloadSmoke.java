import dev.hydev.spectrecore.model.TexturePayload;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TexturePayloadSmoke {
    private static int checks;
    static void yes(boolean value, String msg) { checks++; if (!value) throw new AssertionError(msg); }
    static String encoded(String input) { return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8)); }
    static void reject(String input) {
        checks++;
        try { TexturePayload.checked(input); throw new AssertionError("Accepted invalid payload"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        String hash = "0123456789abcdef".repeat(4);
        String valid = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"+hash+"\"}}}";
        String b64 = encoded(valid);
        yes(TexturePayload.checked(b64).equals(b64),"Accept canonical GameProfile data");
        reject(""); reject("abc @"); reject("a".repeat(16385));
        reject(encoded("{}"));
        reject(encoded("{\"textures\":{\"SKIN\":{\"url\":\"https://evil.example.com/texture/"+hash+"\"}}}"));
        reject(encoded("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net.evil.com/texture/"+hash+"\"}}}"));
        reject(encoded("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/NOTHEX\"}}}"));
        reject(encoded("{\"textures\":{\"CAPE\":{\"url\":\"https://textures.minecraft.net/texture/"+hash+"\"}}}"));
        reject(encoded(valid + "\u0000"));
        System.out.println("PASS TexturePayloadSmoke: " + checks + " assertions");
    }
}
