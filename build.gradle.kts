plugins { java }
group = "dev.hydev"
version = "0.7.4-aura-alpha"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.3.12")
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
    source("tests/ModuleConfigSmoke.java", "tests/PresentationConfigSmoke.java")
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
