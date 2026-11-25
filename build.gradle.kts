plugins { java }
group = "dev.hydev"
version = "0.9.0-dev8"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://nexus.phoenixdevt.fr/repository/maven-public/") }
        filter {
            includeModule("net.Indyuce", "MMOItems-API")
            includeModule("io.lumine", "MythicLib-dist")
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.3.12")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.Indyuce:MMOItems-API:6.10.1-SNAPSHOT") { isTransitive = false }
    compileOnly("io.lumine:MythicLib-dist:1.7.1-SNAPSHOT") { isTransitive = false }
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    archiveBaseName.set("SpectreCore")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val regressionDirectory = layout.buildDirectory.dir("migration-regression")
val compileMigrationRegression = tasks.register<JavaCompile>("compileMigrationRegression") {
    dependsOn(tasks.classes)
    source("tests/MigrationRegression.java", "tests/CommandRoutingRegression.java")
    classpath = sourceSets.main.get().output
    destinationDirectory.set(regressionDirectory)
    options.encoding = "UTF-8"
    options.release.set(21)
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
val migrationRegression = tasks.register<JavaExec>("migrationRegression") {
    dependsOn(compileMigrationRegression)
    classpath = files(regressionDirectory, sourceSets.main.get().output)
    mainClass.set("MigrationRegression")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(migrationRegression) }

val commandRoutingRegression = tasks.register<JavaExec>("commandRoutingRegression") {
    dependsOn(compileMigrationRegression)
    classpath = files(regressionDirectory, sourceSets.main.get().output)
    mainClass.set("CommandRoutingRegression")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(commandRoutingRegression) }

val configTestDirectory = layout.buildDirectory.dir("module-config-tests")
val compileModuleConfigTest = tasks.register<JavaCompile>("compileModuleConfigTest") {
    dependsOn(tasks.classes)
    source("tests/ModuleConfigSmoke.java", "tests/PresentationConfigSmoke.java", "tests/PlayerTransactionJournalSmoke.java", "tests/SupplyOrderRulesSmoke.java", "tests/MithrilRulesSmoke.java", "tests/AmethystResonanceSmoke.java", "tests/MinerWorkshopSmoke.java", "tests/MiningLuckSmoke.java")
    classpath = sourceSets.main.get().output + configurations.compileClasspath.get()
    destinationDirectory.set(configTestDirectory)
    options.encoding = "UTF-8"
    options.release.set(21)
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
val moduleConfigTest = tasks.register<JavaExec>("moduleConfigTest") {
    dependsOn(compileModuleConfigTest)
    classpath = files(configTestDirectory, sourceSets.main.get().output, configurations.compileClasspath)
    mainClass.set("ModuleConfigSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(moduleConfigTest) }

val presentationConfigTest = tasks.register<JavaExec>("presentationConfigTest") {
    dependsOn(compileModuleConfigTest)
    classpath = files(configTestDirectory, sourceSets.main.get().output, configurations.compileClasspath)
    mainClass.set("PresentationConfigSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(presentationConfigTest) }

val slayerTestDirectory = layout.buildDirectory.dir("slayer-progression-tests")
val compileSlayerProgressionSmoke = tasks.register<JavaCompile>("compileSlayerProgressionSmoke") {
    dependsOn(tasks.classes)
    source("tests/SlayerContractSmoke.java", "tests/SlayerProgressionSmoke.java", "tests/SlayerCombatSmoke.java")
    classpath = sourceSets.main.get().output + configurations.compileClasspath.get()
    destinationDirectory.set(slayerTestDirectory)
    options.encoding = "UTF-8"
    options.release.set(21)
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
val slayerProgressionSmoke = tasks.register<JavaExec>("slayerProgressionSmoke") {
    dependsOn(compileSlayerProgressionSmoke)
    classpath = files(slayerTestDirectory, sourceSets.main.get().output, configurations.compileClasspath)
    mainClass.set("SlayerProgressionSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(slayerProgressionSmoke) }

val slayerCombatSmoke = tasks.register<JavaExec>("slayerCombatSmoke") {
    dependsOn(compileSlayerProgressionSmoke)
    classpath = files(slayerTestDirectory, sourceSets.main.get().output, configurations.compileClasspath)
    mainClass.set("SlayerCombatSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(slayerCombatSmoke) }

val transactionJournalSmoke = tasks.register<JavaExec>("transactionJournalSmoke") {
    dependsOn(compileModuleConfigTest)
    classpath = files(configTestDirectory, sourceSets.main.get().output, configurations.compileClasspath)
    mainClass.set("PlayerTransactionJournalSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(transactionJournalSmoke) }

val collectionTestDirectory = layout.buildDirectory.dir("collection-milestones-tests")
val compileCollectionMilestonesSmoke = tasks.register<JavaCompile>("compileCollectionMilestonesSmoke") {
    dependsOn(tasks.classes)
    source("tests/CollectionMilestonesSmoke.java")
    classpath = sourceSets.main.get().output
    destinationDirectory.set(collectionTestDirectory)
    options.encoding = "UTF-8"
    options.release.set(21)
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
val collectionMilestonesSmoke = tasks.register<JavaExec>("collectionMilestonesSmoke") {
    dependsOn(compileCollectionMilestonesSmoke)
    classpath = files(collectionTestDirectory, sourceSets.main.get().output)
    mainClass.set("CollectionMilestonesSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(collectionMilestonesSmoke) }

val supplyOrderRulesSmoke = tasks.register<JavaExec>("supplyOrderRulesSmoke") {
    dependsOn(compileModuleConfigTest)
    classpath = files(configTestDirectory, sourceSets.main.get().output, configurations.compileClasspath)
    mainClass.set("SupplyOrderRulesSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(supplyOrderRulesSmoke) }

val mithrilRulesSmoke = tasks.register<JavaExec>("mithrilRulesSmoke") {
    dependsOn(compileModuleConfigTest)
    classpath = files(configTestDirectory, sourceSets.main.get().output, configurations.compileClasspath)
    mainClass.set("MithrilRulesSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(mithrilRulesSmoke) }

val amethystResonanceSmoke = tasks.register<JavaExec>("amethystResonanceSmoke") {
    dependsOn(compileModuleConfigTest)
    classpath = files(configTestDirectory, sourceSets.main.get().output, configurations.compileClasspath)
    mainClass.set("AmethystResonanceSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(amethystResonanceSmoke) }

val minerWorkshopSmoke = tasks.register<JavaExec>("minerWorkshopSmoke") {
    dependsOn(compileModuleConfigTest)
    classpath = files(configTestDirectory, sourceSets.main.get().output, configurations.compileClasspath)
    mainClass.set("MinerWorkshopSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(minerWorkshopSmoke) }

val miningLuckSmoke = tasks.register<JavaExec>("miningLuckSmoke") {
    dependsOn(compileModuleConfigTest)
    classpath = files(configTestDirectory, sourceSets.main.get().output, configurations.compileClasspath)
    mainClass.set("MiningLuckSmoke")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(miningLuckSmoke) }
