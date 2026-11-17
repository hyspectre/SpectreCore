package dev.hydev.spectrecore.text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

/** MiniMessage for new content, backwards-compatible legacy color formatting for old YAML. */
public final class TextService {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern PARAM = Pattern.compile("\\{([a-zA-Z0-9_-]{1,64})}");
    private TextService() { }

    public static Component component(String source) {
        if (source == null || source.isEmpty()) return Component.empty();
        if (source.contains("<") && source.contains(">")) return MINI.deserialize(source);
        return LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', source));
    }

    /** Safe substitution: player-controlled inputs are inserted as plain text, NEVER parsed as MiniMessage. */
    public static Component render(String source, Map<String, ?> values) {
        if (source == null || source.isEmpty()) return Component.empty();
        if (values == null || values.isEmpty()) return component(source);
        Map<String, String> names = new LinkedHashMap<>();
        TagResolver.Builder resolver = TagResolver.builder();
        Matcher matcher = PARAM.matcher(source);
        StringBuffer template = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!values.containsKey(name)) continue;
            String tag = names.computeIfAbsent(name, key -> "arg" + names.size());
            matcher.appendReplacement(template, Matcher.quoteReplacement("<" + tag + ">"));
        }
        matcher.appendTail(template);
        names.forEach((name, tag) -> resolver.resolver(Placeholder.unparsed(tag,
                String.valueOf(values.get(name) == null ? "" : values.get(name)))));
        return MINI.deserialize(template.toString(), resolver.build());
    }
}
