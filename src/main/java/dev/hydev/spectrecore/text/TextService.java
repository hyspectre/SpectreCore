package dev.hydev.spectrecore.text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** MiniMessage for new content, backwards-compatible legacy color formatting for old YAML. */
public final class TextService {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern PARAM = Pattern.compile("\\{([a-zA-Z0-9_-]{1,64})}");
    private static final Pattern LEGACY_HEX = Pattern.compile("(?i)[&§]x((?:[&§][0-9a-f]){6})");
    private static final Pattern HASH_HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern LEGACY_STYLE = Pattern.compile("(?i)[&§]([0-9a-fk-or])");
    private TextService() { }

    /** MiniMessage is the only renderer. Legacy colour codes are migrated in memory. */
    public static Component component(String source) {
        if (source == null || source.isEmpty()) return Component.empty();
        return MINI.deserialize(legacyToMini(source));
    }

    /** Converts old section/ampersand styles without touching user placeholders or IDs. */
    public static String legacyToMini(String source) {
        if (source == null || source.isEmpty()) return "";
        if (source.indexOf('&') < 0 && source.indexOf('§') < 0) return source;
        Matcher hex = LEGACY_HEX.matcher(source);
        StringBuffer converted = new StringBuffer();
        while (hex.find()) {
            String digits = hex.group(1).replace("&", "").replace("§", "");
            hex.appendReplacement(converted, Matcher.quoteReplacement("<reset><#" + digits + ">"));
        }
        hex.appendTail(converted);
        source = converted.toString();
        Matcher rgb = HASH_HEX.matcher(source);
        converted = new StringBuffer();
        while (rgb.find()) rgb.appendReplacement(converted, Matcher.quoteReplacement("<reset><#" + rgb.group(1) + ">"));
        rgb.appendTail(converted);
        Matcher codes = LEGACY_STYLE.matcher(converted.toString());
        converted = new StringBuffer();
        String[] colors = {"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple",
                "gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white"};
        while (codes.find()) {
            char c = Character.toLowerCase(codes.group(1).charAt(0));
            String replacement;
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                int index = c <= '9' ? c - '0' : c - 'a' + 10;
                replacement = "<reset><" + colors[index] + ">";
            } else replacement = switch (c) {
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'k' -> "<obfuscated>";
                default -> "<reset>";
            };
            codes.appendReplacement(converted, Matcher.quoteReplacement(replacement));
        }
        codes.appendTail(converted);
        return converted.toString();
    }

    /** Safe substitution: player-controlled inputs are inserted as plain text, NEVER parsed as MiniMessage. */
    public static Component render(String source, Map<String, ?> values) {
        if (source == null || source.isEmpty()) return Component.empty();
        if (values == null || values.isEmpty()) return component(source);
        Map<String, String> names = new LinkedHashMap<>();
        TagResolver.Builder resolver = TagResolver.builder();
        Matcher matcher = PARAM.matcher(legacyToMini(source));
        StringBuffer template = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!values.containsKey(name)) continue;
            String tag = names.computeIfAbsent(name, key -> "arg" + names.size());
            matcher.appendReplacement(template, Matcher.quoteReplacement("<" + tag + ">"));
        }
        matcher.appendTail(template);
        names.forEach((name, tag) -> {
            Object value = values.get(name);
            if (value instanceof Component trustedComponent)
                resolver.resolver(Placeholder.component(tag, trustedComponent));
            else resolver.resolver(Placeholder.unparsed(tag, String.valueOf(value == null ? "" : value)));
        });
        return MINI.deserialize(template.toString(), resolver.build());
    }
}
