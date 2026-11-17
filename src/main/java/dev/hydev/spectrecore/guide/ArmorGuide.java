package dev.hydev.spectrecore.guide;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.SpectreCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only native Paper Dialogs: no inventory items, set changes, or combat effects. */
public final class ArmorGuide {
    private record Entry(String name, String shortName, String skill, String set, String ability, int color) { }
    private List<Entry> entries() { return List.of(
        new Entry(Settings.text("equipamiento", "ArmorGuide.etiqueta-058", "Raigambre Ancestral"), Settings.text("equipamiento", "ArmorGuide.etiqueta-059", "Raigambre"), Settings.text("equipamiento", "ArmorGuide.etiqueta-060", "botania"), Settings.text("equipamiento", "ArmorGuide.etiqueta-061", "HYDEV_RAIGAMBRE"), Settings.text("equipamiento", "ArmorGuide.etiqueta-062", "Botania"), Settings.module("equipamiento/config.yml").getInt("libro.colores.botania",0x537D3B)),
        new Entry(Settings.text("equipamiento", "ArmorGuide.etiqueta-063", "Armadura Necrótica"), Settings.text("equipamiento", "ArmorGuide.etiqueta-064", "Necrótica"), Settings.text("equipamiento", "ArmorGuide.etiqueta-065", "necrotica"), Settings.text("equipamiento", "ArmorGuide.etiqueta-066", "NECROTICO"), Settings.text("equipamiento", "ArmorGuide.etiqueta-067", "Trinidad Profana"), Settings.module("equipamiento/config.yml").getInt("libro.colores.necrotica",0x685093)),
        new Entry(Settings.text("equipamiento", "ArmorGuide.etiqueta-068", "Sangre del Laberinto"), Settings.text("equipamiento", "ArmorGuide.etiqueta-069", "Laberinto"), Settings.text("equipamiento", "ArmorGuide.etiqueta-070", "asterion"), Settings.text("equipamiento", "ArmorGuide.etiqueta-071", "HYDEV_LABERINTO"), Settings.text("equipamiento", "ArmorGuide.etiqueta-072", "Asterión"), Settings.module("equipamiento/config.yml").getInt("libro.colores.asterion",0xA25735)),
        new Entry(Settings.text("equipamiento", "ArmorGuide.etiqueta-073", "Voto del Deshonrado"), Settings.text("equipamiento", "ArmorGuide.etiqueta-074", "Deshonrado"), Settings.text("equipamiento", "ArmorGuide.etiqueta-075", "ultimo-juramento"), Settings.text("equipamiento", "ArmorGuide.etiqueta-076", "HYDEV_VOTO"), Settings.text("equipamiento", "ArmorGuide.etiqueta-077", "Último Juramento"), Settings.module("equipamiento/config.yml").getInt("libro.colores.ultimo-juramento",0x6F7486)),
        new Entry(Settings.text("equipamiento", "ArmorGuide.etiqueta-078", "Herencia Dracónica"), Settings.text("equipamiento", "ArmorGuide.etiqueta-079", "Dracónica"), Settings.text("equipamiento", "ArmorGuide.etiqueta-080", "sangre-primer-dragon"), Settings.text("equipamiento", "ArmorGuide.etiqueta-081", "HYDEV_IGNEA"), Settings.text("equipamiento", "ArmorGuide.etiqueta-082", "Sangre del Primer Dragón"), Settings.module("equipamiento/config.yml").getInt("libro.colores.sangre-primer-dragon",0xAA3A29))
    ); }
    private Map<String, String> bonusNames() { return Map.ofEntries(
        Map.entry("max-health", Settings.text("equipamiento", "ArmorGuide.etiqueta-083", "Vida máxima")), Map.entry("max-mana", Settings.text("equipamiento", "ArmorGuide.etiqueta-084", "Maná máximo")),
        Map.entry("magic-damage", Settings.text("equipamiento", "ArmorGuide.etiqueta-085", "Daño mágico (%)")), Map.entry("damage-reduction", Settings.text("equipamiento", "ArmorGuide.etiqueta-086", "Reducción daño (%)")),
        Map.entry("weapon-damage", Settings.text("equipamiento", "ArmorGuide.etiqueta-087", "Daño de arma (%)")), Map.entry("critical-strike-chance", Settings.text("equipamiento", "ArmorGuide.etiqueta-088", "Prob. crítica (%)")),
        Map.entry("critical-strike-power", Settings.text("equipamiento", "ArmorGuide.etiqueta-089", "Poder crítico (%)")),
        Map.entry("fire-damage-reduction", Settings.text("equipamiento", "ArmorGuide.etiqueta-090", "Resist. fuego (%)")),
        Map.entry("potion-regeneration", Settings.text("equipamiento", "ArmorGuide.etiqueta-091", "Regeneración (nivel)")),
        Map.entry("potion-fire_resistance", Settings.text("equipamiento", "ArmorGuide.etiqueta-092", "Resist. fuego (nivel)")),
        Map.entry("potion-speed", Settings.text("equipamiento", "ArmorGuide.etiqueta-093", "Velocidad (nivel)")), Map.entry("movement-speed", Settings.text("equipamiento", "ArmorGuide.etiqueta-094", "Velocidad"))
    ); }
    private final SpectreCorePlugin plugin;
    public ArmorGuide(SpectreCorePlugin plugin) { this.plugin = plugin; }
    private double n(Entry e, String key, double fallback) {
        return plugin.getConfig().getDouble("skills." + e.skill + "." + key, fallback);
    }
    private String value(double number) { return String.format(Locale.ROOT, "%.2f", number).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private String v(Entry e, String key, double fallback) { return value(n(e, key, fallback)); }
    private String pct(Entry e, String key, double fallback) { return value(n(e, key, fallback) * 100) + "%"; }
    private String seconds(Entry e, String key, double fallback) { return value(n(e, key, fallback) / 20) + " s"; }


public void open(Player player) {
    List<io.papermc.paper.registry.data.dialog.ActionButton> buttons = new ArrayList<>();
    for (Entry entry : entries()) {
        buttons.add(io.papermc.paper.registry.data.dialog.ActionButton.builder(Settings.component(entry.name))
            .action(io.papermc.paper.registry.data.dialog.action.DialogAction.staticAction(
                ClickEvent.runCommand("/mago armadura " + entry.skill + " bonos"))).build());
    }
    buttons.add(io.papermc.paper.registry.data.dialog.ActionButton.builder(Settings.component(
            Settings.text("equipamiento", "ArmorGuide.mago-volver", "<gray>Volver a RPG</gray>")))
            .action(io.papermc.paper.registry.data.dialog.action.DialogAction.staticAction(ClickEvent.runCommand("/mago rpg"))).build());
    player.showDialog(io.papermc.paper.dialog.Dialog.create(builder -> builder.empty()
            .base(io.papermc.paper.registry.data.dialog.DialogBase.builder(Settings.component(
                    Settings.text("equipamiento", "ArmorGuide.mago-titulo", "<gold>Armaduras de Hydev</gold>")))
                .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Settings.component(
                    Settings.text("equipamiento", "ArmorGuide.mago-intro", "<gray>Selecciona un conjunto para ver sus estadísticas.</gray>")))))
                .build())
            .type(io.papermc.paper.registry.data.dialog.type.DialogType.multiAction(buttons).build())));
}

public void openDetail(Player player, String skill, String view) {
    Entry selected = entries().stream().filter(e -> e.skill.equals(skill)).findFirst().orElse(null);
    if (selected == null || (!view.equals("bonos") && !view.equals("habilidad"))) {
        open(player); return;
    }
    Plugin mmo = Bukkit.getPluginManager().getPlugin("MMOItems");
    File file = mmo == null ? null : new File(mmo.getDataFolder(), "item-sets.yml");
    YamlConfiguration sets = file != null && file.isFile() ? YamlConfiguration.loadConfiguration(file) : null;
    List<String> lines = view.equals("bonos") ? bonuses(selected, sets) : ability(selected);
    String body = String.join("\n", lines);
    if (body.length() > 4500) body = body.substring(0, 4497) + "...";
    var change = io.papermc.paper.registry.data.dialog.ActionButton.builder(Settings.component(
            Settings.text("equipamiento", "ArmorGuide.mago-cambiar", "<yellow>Bonos / Habilidad</yellow>")))
        .action(io.papermc.paper.registry.data.dialog.action.DialogAction.staticAction(ClickEvent.runCommand(
            "/mago armadura " + selected.skill + (view.equals("bonos") ? " habilidad" : " bonos")))).build();
    var back = io.papermc.paper.registry.data.dialog.ActionButton.builder(Settings.component(
            Settings.text("equipamiento", "ArmorGuide.mago-atras", "<gray>Volver a armaduras</gray>")))
        .action(io.papermc.paper.registry.data.dialog.action.DialogAction.staticAction(ClickEvent.runCommand("/mago armaduras"))).build();
    final String description = body;
    player.showDialog(io.papermc.paper.dialog.Dialog.create(builder -> builder.empty()
        .base(io.papermc.paper.registry.data.dialog.DialogBase.builder(Settings.component(selected.name))
            .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Settings.component(description))))
            .build())
        .type(io.papermc.paper.registry.data.dialog.type.DialogType.multiAction(List.of(change, back)).build())));
}

    private List<String> bonuses(Entry e, YamlConfiguration sets) {
        List<String> lines = new ArrayList<>();
        lines.add(e.name);
        lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-006", ""));
        lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-007", "Efectos del núcleo:"));
        boolean enabled = plugin.getConfig().getBoolean("modules.skills.enabled", true)
            && plugin.getConfig().getBoolean("skills." + e.skill + ".enabled", false);
        if (!enabled) lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-008", "DESACTIVADOS"));
        switch (e.skill) {
            case "asterion" -> {
                lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-009", "({piezas}) +{vida} HP.","piezas",Settings.module("equipamiento/config.yml").getInt("pasivos.piezas",2),"vida",value(Settings.module("equipamiento/config.yml").getDouble("pasivos.laberinto-vida",6))));
                lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-010","(4) Resistencia al empuje +{valor1}.","valor1", v(e, "passive-knockback-resistance", .10)));
            }
            case "ultimo-juramento" -> lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-011", "({piezas}) +{velocidad}% velocidad.","piezas",Settings.module("equipamiento/config.yml").getInt("pasivos.piezas",2),"velocidad",value(Settings.module("equipamiento/config.yml").getDouble("pasivos.voto-velocidad",.04)*100)));
            case "sangre-primer-dragon" -> {
                lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-012", "({piezas}) +{vida} HP y +{velocidad}% velocidad.","piezas",Settings.module("equipamiento/config.yml").getInt("pasivos.piezas",2),"vida",value(Settings.module("equipamiento/config.yml").getDouble("pasivos.dragon-vida",4)),"velocidad",value(Settings.module("equipamiento/config.yml").getDouble("pasivos.dragon-velocidad",.02)*100)));
                lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-013","(4) +{valor1} daño cuerpo a cuerpo; {valor2} menos daño recibido.","valor1", pct(e, "passive-damage-bonus", .03),"valor2", pct(e, "passive-damage-reduction", .03)));
            }
            default -> lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-014", "Sin bonos parciales del núcleo."));
        }
        lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-015", "(4) {e-ability}.", "e-ability", e.ability));
        if (e.skill.equals("ultimo-juramento")) lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-016","Con Juramento Profanado: Blink confirmado crea un eco; próximo golpe +{valor1} durante {valor2}. Requiere habilidad activa.","valor1", pct(e, "blink-next-hit-bonus", .10),"valor2", seconds(e, "blink-next-hit-window-ticks", 50)));
        if (e.skill.equals("sangre-primer-dragon")) lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-017","Con Dracoreliquia: +{valor1} s de bendición mientras la empuñas.","valor1", v(e, "weapon-duration-bonus-seconds", 2)));
        lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-018", ""));
        lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-019", "Bonos MMOItems:"));
        ConfigurationSection bonuses = sets == null ? null : sets.getConfigurationSection(e.set + ".bonuses");
        if (bonuses == null) lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-020", "No se encontró el conjunto. Revisar su configuración."));
        else if (bonuses.getKeys(false).isEmpty()) lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-021", "Sin bonos extra."));
        else for (String count : bonuses.getKeys(false)) {
            ConfigurationSection level = bonuses.getConfigurationSection(count);
            if (level == null) continue;
            lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-022", "({count} piezas)", "count", count));
            for (String key : level.getKeys(false)) {
                if (level.isConfigurationSection(key)) {
                    lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-023","Habilidad extra de MMOItems: {valor1}.","valor1", level.getString(key + ".type", "adicional").replace('_', ' ')));
                    continue;
                }
                lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-024","{valor1}: {level-get-key}","valor1", bonusNames().getOrDefault(key, key.replace('-', ' ')),"level-get-key", level.get(key)));
            }
        }
        lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-025", ""));
        lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-026", "El arma no es necesaria para activar la habilidad de 4 piezas. Los bonos de MMOItems se leen del servidor al abrir esta guía."));
        return lines;
    }

    private List<String> ability(Entry e) {
        List<String> t = new ArrayList<>();
        t.add(e.ability);
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-027", ""));
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-028", "Equipa las cuatro piezas del mismo conjunto en sus ranuras."));
        switch (e.skill) {
            case "botania" -> {
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-029","Acierta {v-e-hits-required-4} golpes cuerpo a cuerpo a enemigos. Máximo {valor2} entre golpes.","v-e-hits-required-4", v(e, "hits-required", 4),"valor2", seconds(e, "hit-window-ticks", 60)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-030","Dos capullos florecen. Lanza {valor1} latigazos de {v-e-lash-damage-3} HP y Lentitud I por {valor3}.","valor1", value(Math.min(8, Math.max(1, n(e, "lash-count", 4)))),"v-e-lash-damage-3", v(e, "lash-damage", 3),"valor3", seconds(e, "slowness-ticks", 30)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-031", "Alcance: {v-e-lash-range-7} bloques. Busca otro enemigo cercano si pierde su objetivo.", "v-e-lash-range-7", v(e, "lash-range", 7)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-032","Primer latigazo: {valor1}; intervalo {valor2}.","valor1", seconds(e, "lash-first-delay-ticks", 20),"valor2", seconds(e, "lash-interval-ticks", 24)));
            }
            case "necrotica" -> {
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-033","Al recibir daño hostil, invoca {valor1} calaveras orbitales.","valor1", value(Math.min(3, Math.max(1, n(e, "skulls", 3))))));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-034", "Cada una dispara hasta {v-e-shots-per-skull-3} veces; {v-e-damage-per-shot-5} HP de daño directo por impacto.", "v-e-shots-per-skull-3", v(e, "shots-per-skull", 3), "v-e-damage-per-shot-5", v(e, "damage-per-shot", 5)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-035","Alcance: {v-e-range-8} bloques. Un disparo global cada {valor2}.","v-e-range-8", v(e, "range", 8),"valor2", seconds(e, "fire-interval-ticks", 60)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-036", "Atacan enemigos cercanos. No atacan jugadores ni mascotas."));
            }
            case "asterion" -> {
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-037", "Gana {v-e-fury-on-hit-12} de furia al golpear y {v-e-fury-on-damage-6} al recibir daño hostil.", "v-e-fury-on-hit-12", v(e, "fury-on-hit", 12), "v-e-fury-on-damage-6", v(e, "fury-on-damage", 6)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-038","Con {furia} de furia, pulsa Shift dos veces en {valor1}.","furia",Settings.module("equipamiento/config.yml").getInt("furia-maxima",100),"valor1", seconds(e, "double-shift-window-ticks", 9)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-039","Golpes seguidos al mismo enemigo: +{valor1} de daño por combo, hasta {valor2}%.","valor1", pct(e, "damage-per-stack", .03),"valor2", value(n(e, "max-combo-stacks", 4) * n(e, "damage-per-stack", .03) * 100)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-040","Cambiar de enemigo o pasar {valor1} entre golpes rompe el combo.","valor1", seconds(e, "combo-gap-ticks", 50)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-041","La furia empieza a decaer tras {valor1} sin combate.","valor1", seconds(e, "fury-decay-delay-ticks", 400)));
            }
            case "ultimo-juramento" -> {
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-042","Pulsa Shift dos veces en {valor1}.","valor1", seconds(e, "double-shift-window-ticks", 9)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-043","+{valor1} daño cuerpo a cuerpo y +{pct-e-speed-bonus-20} velocidad; recibes {valor3} más de daño.","valor1", pct(e, "melee-damage-bonus", .15),"pct-e-speed-bonus-20", pct(e, "speed-bonus", .20),"valor3", pct(e, "damage-taken-increase", .20)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-044","Cada golpe añade {valor1}, hasta {valor2} s desde la activación.","valor1", seconds(e, "extension-per-hit-ticks", 12),"valor2", v(e, "maximum-duration-seconds", 10)));
            }
            case "sangre-primer-dragon" -> {
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-045","Recibe daño hostil o acierta {valor1} golpes cuerpo a cuerpo, con máximo {valor2} entre ambos.","valor1", v(e, "melee-hits-to-activate", 2),"valor2", seconds(e, "melee-hit-window-ticks", 60)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-046","Bendición: +{valor1} daño cuerpo a cuerpo, {valor2} menos daño recibido y +{pct-e-speed-bonus-06} velocidad.","valor1", pct(e, "active-damage-bonus", .06),"valor2", pct(e, "active-damage-reduction", .06),"pct-e-speed-bonus-06", pct(e, "speed-bonus", .06)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-047", "Cura {v-e-heal-on-activation-4} HP sin superar tu máximo.", "v-e-heal-on-activation-4", v(e, "heal-on-activation", 4)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-048", "Daño y reducción se suman a los pasivos de 4 piezas. Aparece un dragón dorado."));
            }
            default -> { }
        }
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-049", ""));
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-050","Duración: {valor1} s.","valor1", v(e, "duration-seconds", e.skill.equals("necrotica") ? 30 : e.skill.equals("botania") ? 8 : e.skill.equals("ultimo-juramento") ? 7 : 10)));
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-051","Recarga: {valor1} s desde activación.","valor1", v(e, "cooldown-seconds", e.skill.equals("botania") ? 30 : e.skill.equals("asterion") ? 50 : e.skill.equals("ultimo-juramento") ? 40 : 45)));
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-052", "1 HP = medio corazón. Daño base antes de defensas."));
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-053", "Quitar una pieza cancela el efecto. La recarga continúa mientras el servidor siga encendido."));
        return t;
    }

}
