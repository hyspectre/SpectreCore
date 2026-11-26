import dev.hydev.spectrecore.config.PresentationMigrations;
import java.nio.file.Files;
import java.nio.file.Path;

/** No Bukkit dependencies: exact legacy migration and preservation checks. */
public final class PresentationMigrationsSmoke {
    private static int count;
    private static void check(boolean condition, String label) {
        count++;
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("spectrecore-ui-migration-");
        Path menu = folder.resolve("ui/misiones.yml");
        Files.createDirectories(menu.getParent());
        String original = "# custom comment stays\n"
            + "lore: \"Objeto de MMOItems entregado por SpectreCore.\"\n"
            + "progreso: \"Progreso: <white>/spectrecore slayer estado\"\n"
            + "nombre-personalizado: \"Me encantan los dragones\"\n";
        Files.writeString(menu, original);
        check(PresentationMigrations.migrate(folder.toFile(), menu.toFile(), "ui/misiones.yml"),
              "Expected one-time migration");
        String migrated = Files.readString(menu);
        check(migrated.contains("Consulta tus misiones desde cualquier lugar."), "Replaces technical lore");
        check(migrated.contains("Consulta tu progreso en el apartado de niveles."), "Removes menu command");
        check(migrated.contains("Me encantan los dragones"), "Preserves custom wording");
        Path backup = folder.resolve("backups/presentation-v3/ui/misiones.yml");
        check(Files.readString(backup).equals(original), "Backup is byte-for-byte original");
        check(!PresentationMigrations.migrate(folder.toFile(), menu.toFile(), "ui/misiones.yml"),
              "Second migration idempotent");
        check(Files.readString(backup).equals(original), "Backup not overwritten");
        boolean rejected = false;
        try { PresentationMigrations.migrate(folder.toFile(), menu.toFile(), "ui/../../unsafe.yml"); }
        catch (java.io.IOException correct) { rejected = true; }
        check(rejected, "Unsafe migration path rejected");
        check(PresentationMigrations.rewriteKnownCopy("Texto personalizado sin cambios")
              .equals("Texto personalizado sin cambios"), "Arbitrary custom text not modified");
        check(PresentationMigrations.isLegacyGuidePoints("resonancia", java.util.List.of(
                "<gray>Acumula carga con la minería.",
                "<gray>La carga completa abre una oportunidad.",
                "<gray>Agáchate para aprovecharla.")), "Recognizes exact old resonance guide");
        check(!PresentationMigrations.isLegacyGuidePoints("resonancia", java.util.List.of(
                "<gray>Mi guía personalizada.")), "Preserves customized lists");
        check(PresentationMigrations.isLegacyGuidePoints("tesoros", java.util.List.of(
                "<gray>Minería, caza y cosechas especiales.",
                "<gray>Raridades y oportunidades distintas.",
                "<gray>Busca fuentes de botín poco comunes.")), "Recognizes old treasure guide");
        check(!PresentationMigrations.isLegacyGuidePoints("tesoros", java.util.List.of(
                "<gray>Mi tesoro personalizado.")), "Preserves customized treasure guide");
        check(PresentationMigrations.rewriteKnownCopy("cargada: \"<aqua>✦ Resonancia restaurada: <white>{carga}/100\"")
                .contains("{carga}/{max}"), "Migrates old charge capacity text");
        check(!PresentationMigrations.isLegacyGuidePoints("otra", java.util.List.of()), "Unknown pages unchanged");
        check(PresentationMigrations.rewriteKnownCopy("\"<reset><red>Contrato rechazado: {motivo}\"")
              .equals("\"<red>{motivo}\""), "Migrates the old Slayer refusal wrapper");
        check(PresentationMigrations.rewriteKnownCopy("\"<reset><red>No puedes firmar este contrato: {motivo}\"")
              .equals("\"<red>{motivo}\""), "Migrates the old Jobs refusal wrapper");
        check(PresentationMigrations.rewriteKnownCopy("\"<gold>Mi contrato: {motivo}\"")
              .equals("\"<gold>Mi contrato: {motivo}\""), "Custom contract wrapper retained");
        System.out.println("PASS PresentationMigrationsSmoke: " + count + " checks");
    }
}
