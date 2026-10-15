$ErrorActionPreference = 'Stop'

if (-not (Test-Path '.\pom.xml')) {
    Write-Error 'Open a terminal in the project folder that contains pom.xml.'
    exit 1
}

try {
    java -version
} catch {
    Write-Error 'Java 17 or newer is required. Install a JDK and configure JAVA_HOME/PATH.'
    exit 1
}

try {
    mvn -version
} catch {
    Write-Error 'Maven is not available in PATH. Install Maven 3.8+ and reopen VS Code.'
    exit 1
}

mvn spring-boot:run
