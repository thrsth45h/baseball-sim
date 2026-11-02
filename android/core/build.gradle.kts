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
    doFirst {
        val root = project.rootDir.canonicalFile.toPath()
        val build = layout.buildDirectory.get().asFile.canonicalFile.toPath()
        val audit = pennantAudit.get().asFile.canonicalFile.toPath()
        check(build.startsWith(root) && audit.startsWith(root) && audit.parent == build && audit.fileName.toString() == "pennant-audit") {
            "Refusing to initialize an audit output outside the declared project build directory"
        }
        // This task owns only this generated directory. Raw fixtures and saved evidence are elsewhere.
        project.delete(audit.toFile())
    }
}
tasks.register<Sync>("exportAuditBundle") {
    dependsOn(tasks.test)
    into(layout.buildDirectory.dir("audit-bundle"))
    from(sourceSets["main"].output) { into("classes") }
    from(sourceSets["test"].output) { into("classes") }
    from(configurations.testRuntimeClasspath) { into("lib") }
    from(pennantAudit) { into("audit") }
}
