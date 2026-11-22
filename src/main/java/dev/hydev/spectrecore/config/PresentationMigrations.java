package dev.hydev.spectrecore.config;

import dev.hydev.spectrecore.storage.AtomicFileStore;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Conservative migration of known, shipped presentation copy. Never rewrite
 * unknown/custom text. Backups are kept outside the live UI/modules folders.
 */
public final class PresentationMigrations {
    private record Rule(String oldText, String newText) { }
    private static final List<Rule> RULES = List.of(
        new Rule("Conserva el uso del Skript: ejecuta estos comandos como jugador.", "Esta acción solo puede realizarse dentro del juego."),
        new Rule("No se confirmó el cobro mediante Vault; el contrato no cambió.", "No se pudo confirmar el cobro; tu contrato sigue intacto."),
        new Rule("No se pudo generar el Teléfono Rojo de MMOItems. No se ha cobrado.", "No se pudo preparar el Teléfono Rojo. No se ha cobrado."),
        new Rule("Vault no confirmó el pago. No se ha entregado el objeto.", "No se confirmó el pago. No se ha entregado el objeto."),
        new Rule("Ese módulo está desactivado en SpectreCore.", "Esta función está desactivada."),
        new Rule("No se puede activar SpectreCore junto con {obsolete}. Retira su JAR para evitar habilidades/cocinas duplicadas.", "Hay sistemas incompatibles activos. Consulta a administración antes de continuar."),
        new Rule("Minería desactivada en SpectreCore.", "La minería especial está desactivada."),
        new Rule("SpectreCore {getpluginmeta-getversion}: habilidades={skillsenabled}, cocina={cookingenabled}.", "Hydev {getpluginmeta-getversion}: habilidades={skillsenabled}, cocina={cookingenabled}."),
        new Rule("Se migraron ajustes de SpectreSkills/config.yml (original intacto).", "Se actualizaron ajustes anteriores; el archivo original sigue intacto."),
        new Rule("SpectreCore: parámetros de habilidades y recetas recargados. Para cambiar módulos activos, reinicia el servidor.", "Se actualizaron habilidades y recetas. Para cambiar módulos activos, reinicia el servidor."),
        new Rule("✦ SpectreCore {getpluginmeta-getversion}", "✦ Hydev {getpluginmeta-getversion}"),
        new Rule("✦ SpectreCore ", "✦ Hydev "),
        new Rule("SpectreCore — telemetría:", "Hydev — diagnóstico:"),
        new Rule("WorldGuard: esta región no permite colocar o retirar modelos.", "Esta región no permite colocar ni retirar modelos."),
        new Rule("MMOItems absent", "Sistema de equipamiento no disponible"),
        new Rule("Este comando conserva el uso del Skript: debe ejecutarlo un jugador.", "Esta acción debe realizarse dentro del juego."),
        new Rule("No se pudo ejecutar el desbloqueo. Revisa LuckPerms.", "No se pudo activar el desbloqueo. Avisa a administración."),
        new Rule("No se pudo abrir el menú. Revisa zMenu.", "El catálogo no está disponible por ahora."),
        new Rule("Trabajos: no se pudo pagar; revisa Vault/economía o eco. No se consume cooldown.", "Trabajos: no se pudo confirmar el pago. No se aplicó el tiempo de espera."),
        new Rule("LuckPerms no está disponible; no se ha cobrado.", "No se pudo registrar el contrato. No se ha cobrado."),
        new Rule("No se pudo confirmar el pago mediante Vault.", "No se pudo confirmar el pago."),
        new Rule("SpectreCore cocina: {stations-size} estaciones, {recipes-size} recetas.", "Cocina: {stations-size} estaciones, {recipes-size} recetas."),
        new Rule("Falta recompensa MMOItems: {rewardid}", "No se pudo generar la recompensa culinaria: {rewardid}"),
        new Rule("API de MMOItems incompatible: {valor1}: {ex-getmessage}", "No se pudo preparar la recompensa: {valor1}: {ex-getmessage}"),
        new Rule("WorldGuard: no puedes colocar ni retirar estaciones en esta región.", "No puedes colocar ni retirar estaciones en esta región."),
        new Rule("Minotauro muerto sin sesiones registradas. Configura callbacks /spectrecore portal registrar y /spectrecore portal salir en Dungeons+.", "El Minotauro fue derrotado sin participantes registrados. Revisa la gestión de la expedición."),
        new Rule("No se confirmó tu salida. Puedes reintentar; revisa Dungeons+ con administración.", "No se confirmó tu salida. Intenta nuevamente o avisa a administración."),
        new Rule("Dungeons+ rechazó el comando de salida", "La expedición rechazó la solicitud de salida"),
        new Rule("Agricultura: falta CONSUMABLE:{id} o MMOItems no pudo generarlo.", "No se pudo generar el hallazgo agrícola ({id})."),
        new Rule("Almas: no se pudo generar {type}:{id}. Revisa MMOItems y el ID.", "No se pudo generar el hallazgo de combate ({type}:{id})."),
        new Rule("Minería: no se pudo generar {type}:{id}. Revisa el ID, MMOItems y su API.", "No se pudo generar el hallazgo minero ({type}:{id})."),
        new Rule("MMOItems: ", "Objeto: "),
        new Rule("MMOItems ausente: habilidades de set desactivadas; solo funciona demo.", "No se pudieron cargar las habilidades de los conjuntos."),
        new Rule("MMOItems detectado: identificación por tipo e ID habilitada.", "Las habilidades de los conjuntos están disponibles."),
        new Rule("La API de MMOItems difiere; el modo de sets queda desactivado.", "No se pueden consultar las propiedades del equipamiento."),
        new Rule("Efectos del núcleo:", "Bonificaciones del conjunto:"),
        new Rule("Sin bonos parciales del núcleo.", "Sin bonificaciones parciales del conjunto."),
        new Rule("Bonos MMOItems:", "Bonificaciones adicionales:"),
        new Rule("Habilidad extra de MMOItems: {valor1}.", "Habilidad adicional: {valor1}."),
        new Rule("El arma no es necesaria para activar la habilidad de 4 piezas. Los bonos de MMOItems se leen del servidor al abrir esta guía.", "No necesitas empuñar un arma para activar la habilidad de las cuatro piezas. Consulta aquí las bonificaciones actuales."),
        new Rule("Objeto de MMOItems entregado por SpectreCore.", "Consulta tus misiones desde cualquier lugar."),
        new Rule("SpectreCore valida la misión antes de cobrar.", "El contrato será validado antes de cobrar."),
        new Rule("Progreso: <white>/spectrecore slayer estado", "Consulta tu progreso en el apartado de niveles."),
        new Rule("Desbloquea: <white>Espada FILOPODRIDO", "Desbloquea receta: <white>Filo Podrido"),
        new Rule("Desbloquea: <white>ZOMBIEARMOR_1 y ZOMBIEARMOR_2", "Desbloquea recetas: <white>Armadura Zombi I–II"),
        new Rule("Desbloquea: <white>ZOMBIEARMOR_3/4 y Nigromante Tier I", "Recetas: <white>Armadura Zombi III–IV <dark_gray>· <gray>Acceso al Liche I"),
        new Rule("Desbloquea: <white>COLMILLO_PUTREFACTO y Revenant Tier II", "Receta: <white>Colmillo Putrefacto <dark_gray>· <gray>Monarquía II"),
        new Rule("Desbloquea: <white>RENACIDO_1 y RENACIDO_2", "Desbloquea recetas: <white>Renacido I–II"),
        new Rule("Desbloquea: <white>RENACIDO_3 y RENACIDO_4", "Desbloquea recetas: <white>Renacido III–IV"),
        new Rule("Desbloquea: <white>Espada SABLE_NECROTICO", "Desbloquea receta: <white>Sable Necrótico"),
        new Rule("Desbloquea: <white>NECROTICO_1 y NECROTICO_2", "Desbloquea recetas: <white>Necrótico I–II"),
        new Rule("Desbloquea: <white>NECROTICO_3/4 y Revenant Tier III", "Recetas: <white>Necrótico III–IV <dark_gray>· <gray>Monarquía III"),
        new Rule("Desbloquea: <white>Espada NECROESPADA", "Desbloquea receta: <white>Necroespada"),
        new Rule("de la configuración activa de SpectreCore.", "de las condiciones vigentes de tu oficio."),
        new Rule("Abre /mago dentro de Minecraft.", "Consulta al Mago dentro del juego."),
        new Rule("Abre /mago armaduras para consultar los cinco conjuntos y sus bonos actuales.", "Consulta los cinco conjuntos y sus bonificaciones en Armaduras."),
        new Rule("Las habilidades de conjuntos se explican en /mago armaduras.", "Las habilidades de los conjuntos se explican en Armaduras."),
        new Rule("Los sistemas de juego siguen funcionando con sus comandos actuales.", "Cada profesión tiene sus propias actividades y recompensas."),
        new Rule("Consulta /spectrecore trabajo para ver tus opciones actuales. Las recompensas están definidas en YAML.", "Explora los contratos de Minería, Agricultura y Caza."),
        new Rule("Los objetivos y recompensas se configuran en módulos de Slayer.", "Cada cacería tiene objetivos, dificultades y recompensas distintas."),
        new Rule("Las tareas de cosecha pueden generar tesoros según las probabilidades y requisitos del servidor. Consulta los permisos y categorías disponibles.", "La cosecha puede revelar tesoros especiales. Descubre qué cultivos son más valiosos."),
        new Rule("Cada fuente tiene requisitos, probabilidades y cooldowns configurables.", "Cada fuente tiene sus propios hallazgos y tiempos de espera."),
        new Rule("La duración de la ventana y la carga máxima dependen de la configuración.", "Presta atención a tu carga para aprovechar su máxima energía."),
        new Rule("La proximidad, el tiempo de espera y la salida dependen de la configuración del servidor.", "Mantente cerca de tus compañeros para entrar a la expedición."),
        new Rule("Algunas dependen de MythicMobs, zMenu, LuckPerms o la economía.", "Explora los acontecimientos y oportunidades que ofrece Hydev."),
        new Rule("<aqua>⚔ RPG</aqua>", "<aqua>⚔ Combate</aqua>"),
        new Rule("<gray>RPG</gray>", "<gray>Combate</gray>"),
        new Rule("<green>Guías del servidor</green>", "<green>Exploración y aventuras</green>"),
        new Rule("<red>Misiones Slayer</red>", "<red>Cacerías de la Monarquía</red>"),
        new Rule("<gray>Guías breves, sin tecnicismos.", "<gray>Consejos prácticos para tu camino."),
        new Rule("<dark_gray>Los contratos se gestionan aparte.", "<dark_gray>Los contratos se consultan por separado."),
        new Rule("<gray>Consulta los sistemas del servidor sin salir de Minecraft.", "<gray>Descubre los secretos, desafíos y oportunidades de Hydev."),
        new Rule("<gray>Acepta misiones, elimina los objetivos de tu categoría y progresa por tiers.", "<gray>Acepta contratos, derrota criaturas y avanza en los rangos de cacería."),
        new Rule("<gray>Conoce los encantamientos personalizados cuando estén implementados.", "<gray>Los nuevos encantamientos permanecen ocultos por ahora."),
        new Rule("<gray>Esta sección mostrará los accesorios del servidor.", "<gray>Los accesorios especiales todavía no están al alcance de los aventureros."),
        new Rule("<gray>Las habilidades de los conjuntos se explican en Armaduras. Se ampliará esta sección con otras habilidades.", "<gray>Cada armadura encierra un poder distinto: aprende cuándo y cómo despertarlo."),
        new Rule("<gray>Activa cocinas con un mechero, prepara ingredientes y sirve platos. Pulsa Recetario para consultar las recetas disponibles.", "<gray>Enciende la fogata, prepara los ingredientes y sirve tus platos. Descubre qué secretos guarda cada receta."),
        new Rule("<gray>Conoce los oficios y sus estaciones. Cada profesión tiene sus propias actividades y recompensas.", "<gray>Minería, Agricultura y Caza ofrecen desafíos, rangos y recompensas. También puedes perfeccionar tu cocina.")
    );

    private PresentationMigrations() { }

    /** Pure rewrite for smoke tests; IDs, technical config keys and unrelated text survive. */
    public static String rewriteKnownCopy(String input) {
        if (input == null || input.isEmpty()) return input;
        String output = input;
        for (Rule rule : RULES) output = output.replace(rule.oldText, rule.newText);
        return output;
    }

    /**
     * When the server already has an older YAML, replace only the old default
     * phrases and preserve a one-time copy. No attempt to modify custom prose.
     */
    public static boolean migrate(File dataFolder, File target, String relativePath) throws IOException {
        if (!relativePath.matches("(?:modules|ui)/[a-zA-Z0-9_./-]+\\.yml")
                || relativePath.contains("..") || target.isDirectory())
            throw new IOException("Ruta de migración no permitida: " + relativePath);
        if (!target.isFile()) return false;
        String original = Files.readString(target.toPath(), StandardCharsets.UTF_8);
        String revised = rewriteKnownCopy(original);
        if (original.equals(revised)) return false;
        File backup = new File(dataFolder, "backups/presentation-v2/" + relativePath);
        if (!backup.exists()) {
            Files.createDirectories(backup.toPath().getParent());
            Files.copy(target.toPath(), backup.toPath());
        }
        AtomicFileStore.writeUtf8(target.toPath(), revised);
        return true;
    }
}
