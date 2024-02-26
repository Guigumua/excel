plugins {
    id("java")
}

group = "com.github.guigumua"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":excel-runtime"))

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    annotationProcessor("org.projectlombok:lombok:1.18.30")
    annotationProcessor("com.google.auto.service:auto-service:1.0-rc7")

    compileOnly("com.google.auto.service:auto-service:1.0-rc7")
    compileOnly("org.dhatim:fastexcel:0.17.0")
    compileOnly("org.dhatim:fastexcel-reader:0.17.0")
    compileOnly("org.jetbrains:annotations:22.0.0")
    compileOnly("org.projectlombok:lombok:1.18.30")
}


tasks.compileJava {
    options.encoding = "UTF-8"
}

