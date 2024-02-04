plugins {
    id("java")
}

group = "com.github.guigumua"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    annotationProcessor("com.google.auto.service:auto-service:1.0-rc7")
    compileOnly("com.google.auto.service:auto-service:1.0-rc7")
    compileOnly("org.dhatim:fastexcel:0.16.4")
}

tasks.test {
    useJUnitPlatform()
}
