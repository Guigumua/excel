plugins {
    id("java")
}

group = "com.github.guigumua"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    annotationProcessor(project(":excel-apt"))
    compileOnly(project(":excel-apt"))
    implementation("org.dhatim:fastexcel:0.16.4")
    implementation("org.dhatim:fastexcel-reader:0.16.4")
    implementation(project(":excel-runtime"))
}

tasks.compileJava {
    options.encoding = "UTF-8"
}
tasks.test {
    useJUnitPlatform()
}
