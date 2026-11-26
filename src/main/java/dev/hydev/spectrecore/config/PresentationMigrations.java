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
        new Rule("<gray>Conoce los oficios y sus estaciones. Cada profesión tiene sus propias actividades y recompensas.", "<gray>Minería, Agricultura y Caza ofrecen desafíos, rangos y recompensas. También puedes perfeccionar tu cocina."),
        new Rule("No se pudo ejecutar el pago. Revisa el plugin de economía.", "No se pudo confirmar el pago. Inténtalo más tarde."),
        new Rule("No se pudo mover a Cygnus; revisa su plugin y configuración.", "Cygnus no puede desplazarse en este momento."),
        new Rule("No está disponible {offer-type}:{offer-id}. Avisa a administración.", "Esta recompensa no está disponible. Avisa a administración."),
        new Rule("Permiso configurado inválido.", "Este desbloqueo no está disponible. Avisa a administración."),
        new Rule("Cofre mal configurado: {error-getmessage}", "Este cofre no está disponible. Avisa a administración."),
        new Rule("{prefix}<reset><red>{error-getmessage}", "{prefix}<reset><red>No se pudieron actualizar las recetas. Avisa a administración."),
        new Rule("Configuracion y recetas recargadas.", "Recetas y preparaciones actualizadas."),
        new Rule("Tier {m-tier}", "Rango {m-tier}"),
        new Rule("Categoría: revenant, necromancer o spider.", "Esta cacería no existe."),
        new Rule("Tier inválido. Usa 1, 2 o 3.", "Elige un rango del 1 al 3."),
        new Rule("No tienes permiso para ese tier.", "Todavía no puedes acceder a ese rango."),
        new Rule("Tier {tier} · Objetivo: {m-required}", "Rango {tier} · Objetivo: {m-required}"),
        new Rule("No se pudo invocar {mob}. Tu objetivo se conserva. Reintenta con /spectrecore slayer invocar {category}", "El jefe no pudo aparecer. Conservas el contrato y podrás reintentar la invocación."),
        new Rule("Tu jefe desapareció. Conservaste el contrato; usa /spectrecore slayer invocar {category} para reintentarlo.", "El jefe desapareció. Conservas el contrato y podrás volver a invocarlo."),
        new Rule("Tier válido: 1, 2 o 3.", "Nivel válido: 1, 2 o 3."),
        new Rule("{mmo-type-held}:{mmo-id-held}", "Herramienta inspeccionada"),
        new Rule("Modo: <reset><white>{valor1} <reset><gray>| Pico configurado: <reset><white>{valor2}", "Estado de extracción: <reset><white>Verificado"),
        new Rule("Ese módulo está desactivado.", "Esta función está desactivada."),
        new Rule("Este módulo está desactivado.", "Esta función está desactivada."),
        new Rule("{error-getmessage}. Se conserva la última configuración válida.", "No se pudieron actualizar los ajustes. Se conserva la configuración anterior."),
        new Rule("Menú inválido: {error}; se conserva la última versión válida.", "No se pudo actualizar la guía. Se conserva la versión anterior."),
        new Rule("Modelo inválido: {error}", "No se pudo preparar ese modelo. Avisa a administración."),
        new Rule("models/settings.yml inválido; se conservan límites anteriores.", "No se pudieron actualizar los ajustes de modelos."),
        new Rule("Toast no disponible o desactivado. Verifica la consola y los ajustes.", "Esta notificación no está disponible."),
        new Rule("Habilidades y cooldowns reiniciados.", "Habilidades y tiempos de espera reiniciados.")
,
        new Rule("Comprar acceso remoto a misiones mediante Vault y MMOItems", "Obtener el Teléfono Rojo para consultar tus misiones"),
        new Rule("Contratar una misión Slayer con Vault", "Contratar una cacería"),
        new Rule("Abandonar un contrato Slayer con Vault", "Abandonar una cacería"),
        new Rule("Guía de armaduras mediante Dialogs", "Catálogo de armaduras y bonificaciones"),
        new Rule("Sistemas RPG y administración de Hydev", "Aventuras y herramientas de Hydev"),
        new Rule("Sistema Hydev trabajo", "Contratos y oficios"),
        new Rule("Sistema Hydev activarslayer", "Iniciar una cacería"),
        new Rule("Sistema Hydev cancelarslayer", "Cancelar una cacería"),
        new Rule("Sistema Hydev resetslayer", "Reiniciar una cacería"),
        new Rule("Sistema Hydev slayerstats", "Consultar el progreso de cacerías"),
        new Rule("Sistema Hydev slayerinvocar", "Reintentar una aparición pendiente"),
        new Rule("Sistema Hydev hydevportaljoin", "Registrar un participante"),
        new Rule("Sistema Hydev hydevportalleave", "Registrar la salida de un participante"),
        new Rule("Sistema Hydev hydevportalcleanup", "Retirar portales activos"),
        new Rule("Sistema Hydev hydevportalstatus", "Consultar el estado del portal"),
        new Rule("Sistema Hydev hydevportaldemo", "Previsualizar el portal"),
        new Rule("\"<gray>ᴄᴏɴᴇᴄᴛᴀ <white>{v-e-hits-required-4} ɢᴏʟᴘᴇs\\n<gray>ᴇɴ <yellow>{valor2} <gray>ᴘᴀʀᴀ ᴀᴄᴛɪᴠᴀʀ\"", "\"<gray>Encadena <white>{v-e-hits-required-4} golpes\\n<gray>Máximo <yellow>{valor2} <gray>entre golpes.\""),
        new Rule("\"<gray>ᴀʟᴄᴀɴᴄᴇ <white>{v-e-range-8} ʙʟᴏǫᴜᴇs\"", "\"<gray>Alcance <white>{v-e-range-8} bloques\\n<gray>Un disparo cada <white>{valor2}.\\n<gray>Sin enemigo cercano, conserva almas.\""),
        new Rule("\"<aqua>✦ Resonancia restaurada: <white>{carga}/100\"", "\"<aqua>✦ Resonancia restaurada: <white>{carga}/{max}\""),
        new Rule("\"<reset><red>Contrato rechazado: {motivo}\"", "\"<red>{motivo}\""),
        new Rule("\"<reset><red>No puedes firmar este contrato: {motivo}\"", "\"<red>{motivo}\"")
    );

    private PresentationMigrations() { }

    /** Replace an exact packaged list with the current default; custom guides stay untouched. */
    public static boolean isLegacyGuidePoints(String page, List<String> current) {
        List<String> legacy = switch (page) {
            case "cocina" -> List.of(
                    "<gray>Enciende una estación con un mechero.",
                    "<gray>Usa utensilios, controla la cocción.",
                    "<gray>Sirve porciones y limpia los restos.");
            case "accesorios" -> List.of(
                    "<gray>Descubre objetos de la Cripta y el mundo.",
                    "<gray>Sus bonificaciones se acumulan.",
                    "<gray>Todas las reliquias absorbidas aportan bonos.",
                    "<gray>El Relicario muestra tu progreso.");
            case "resonancia" -> List.of(
                    "<gray>Acumula carga con la minería.",
                    "<gray>La carga completa abre una oportunidad.",
                    "<gray>Agáchate para aprovecharla.");
            case "habilidades" -> List.of(
                    "<gray>Sus condiciones cambian por armadura.",
                    "<gray>Lee señales, alcance y recarga.",
                    "<gray>Algunas exigen golpes o daño recibido.");
            case "tesoros" -> List.of(
                    "<gray>Minería, caza y cosechas especiales.",
                    "<gray>Raridades y oportunidades distintas.",
                    "<gray>Busca fuentes de botín poco comunes.");
            case "portales" -> List.of(
                    "<gray>Reúne jugadores cerca del portal.",
                    "<gray>Mantengan la formación al ingresar.",
                    "<gray>Las aventuras premian la cooperación.");
            default -> null;
        };
        return legacy != null && legacy.equals(current);
    }


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
        File backup = new File(dataFolder, "backups/presentation-v3/" + relativePath);
        if (!backup.exists()) {
            Files.createDirectories(backup.toPath().getParent());
            Files.copy(target.toPath(), backup.toPath());
        }
        AtomicFileStore.writeUtf8(target.toPath(), revised);
        return true;
    }
}
