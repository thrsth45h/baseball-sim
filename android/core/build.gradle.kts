plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.code.gson:gson:2.13.2")
}
val pennantAudit = layout.buildDirectory.dir("pennant-audit")
tasks.test {
    useJUnit()
    systemProperty("pennant.audit.dir", pennantAudit.get().asFile.absolutePath)
    outputs.dir(pennantAudit)
}
tasks.register<Sync>("exportAuditBundle") {
    dependsOn(tasks.testClasses)
    into(layout.buildDirectory.dir("audit-bundle"))
    from(sourceSets["main"].output) { into("classes") }
    from(sourceSets["test"].output) { into("classes") }
    from(configurations.testRuntimeClasspath) { into("lib") }
    from(pennantAudit) { into("audit") }
}
