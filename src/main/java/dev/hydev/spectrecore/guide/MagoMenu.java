package dev.hydev.spectrecore.guide;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.text.TextService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/** YAML-only, read-only navigation. Buttons can only go to validated internal pages. */
public final class MagoMenu {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");
    private final SpectreCorePlugin plugin;
    private final ArmorGuide armors;
    private volatile YamlConfiguration pages;
    private final Map<Player, Long> nextAccess = Collections.synchronizedMap(new WeakHashMap<>());

    public MagoMenu(SpectreCorePlugin plugin) throws IOException, InvalidConfigurationException {
        this.plugin = plugin; this.armors = plugin.armors();
        reload();
    }
    public void reload() throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), "ui/mago.yml");
        if (!file.exists()) plugin.saveResource("ui/mago.yml", false);
        if (!file.isFile() || file.length() > 64 * 1024) throw new IOException("ui/mago.yml no es un archivo válido o supera 64 KiB");
        YamlConfiguration fresh = new YamlConfiguration();
        fresh.load(file);
        ConfigurationSection root = fresh.getConfigurationSection("pages");
        if (root == null || !root.isConfigurationSection("inicio") || root.getKeys(false).size() > 40)
            throw new IOException("ui/mago.yml: falta pages.inicio o hay demasiadas páginas");
        for (String name : root.getKeys(false)) {
            if (!ID.matcher(name).matches()) throw new IOException("Identificador de página inválido: " + name);
            ConfigurationSection page = root.getConfigurationSection(name);
            if (page == null || !page.isString("title") || !page.isString("body"))
                throw new IOException("Página incompleta: " + name);
            if (page.getString("title", "").length() > 256 || page.getString("body", "").length() > 3200)
                throw new IOException("Página demasiado extensa: " + name);
            List<?> links = page.getList("buttons", List.of());
            if (links.size() > 12) throw new IOException("Demasiados botones en " + name);
            for (Object raw : links) {
                if (!(raw instanceof Map<?, ?> map)) throw new IOException("Botón inválido en " + name);
                Object target = map.get("page"), title = map.get("label");
                if (!(target instanceof String to) || !(title instanceof String text) || text.length() > 120
                        || !ID.matcher(to).matches() || (!root.isConfigurationSection(to) && !Set.of("armaduras", "recetas").contains(to)))
                    throw new IOException("Destino de botón inválido en " + name);
            }
        }
        pages = fresh; // commit only after full validation
    }
    public boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextService.component(pages.getString("console-message", "<gray>Abre /mago dentro de Minecraft.</gray>"))); return true;
        }
        long now = System.nanoTime();
        Long gate = nextAccess.get(player);
        if (gate != null && now - gate < 160_000_000L) return true;
        nextAccess.put(player, now);
        String section = args.length == 0 ? "inicio" : args[0].toLowerCase(Locale.ROOT);
        if (section.equals("armaduras")) {
            if (armors != null) armors.open(player);
            else player.sendMessage(TextService.component("<red>El catálogo de armaduras está desactivado."));
            return true;
        }
        if (section.equals("recetas")) { return plugin.getServer().dispatchCommand(player, "spectrecore cocina recetas"); }
        if (section.equals("armadura") && args.length == 3) {
            if (armors != null) armors.openDetail(player, args[1], args[2]);
            else player.sendMessage(TextService.component("<red>El catálogo de armaduras está desactivado."));
            return true;
        }
        if (!ID.matcher(section).matches() || !pages.isConfigurationSection("pages." + section)) {
            player.sendMessage(TextService.component(pages.getString("invalid-page", "<red>No encuentro ese conocimiento.</red>")));
            return true;
        }
        show(player, section);
        return true;
    }
    public List<String> complete(String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>(pages.getConfigurationSection("pages").getKeys(false));
        values.add("armaduras"); values.add("recetas");
        return values.stream().filter(s -> s.startsWith(prefix)).sorted().toList();
    }
    public void show(Player player, String id) {
        ConfigurationSection page = pages.getConfigurationSection("pages." + id);
        if (page == null) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (Map<?, ?> button : page.getMapList("buttons")) {
            String target = String.valueOf(button.get("page"));
            // Built from startup-validated identifiers, never from an arbitrary command in YAML.
            buttons.add(ActionButton.builder(TextService.component(String.valueOf(button.get("label"))))
                    .action(DialogAction.staticAction(ClickEvent.runCommand("/mago " + target)))
                    .build());
        }
        if (buttons.isEmpty()) buttons.add(ActionButton.builder(TextService.component(pages.getString("close", "Cerrar"))).build());
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(TextService.component(page.getString("title", "El Mago")))
                        .body(List.of(DialogBody.plainMessage(TextService.component(page.getString("body", "")))))
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        player.showDialog(dialog);
    }
    public void forget(Player player) { nextAccess.remove(player); }
    public void close() { nextAccess.clear(); }
}
