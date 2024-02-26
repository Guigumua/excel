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
    implementation("org.dhatim:fastexcel:0.17.0")
    implementation("org.dhatim:fastexcel-reader:0.17.0")
    compileOnly("org.jetbrains:annotations:22.0.0")
    compileOnly("org.projectlombok:lombok:1.18.30")
}

tasks.test {
    useJUnitPlatform()
}
