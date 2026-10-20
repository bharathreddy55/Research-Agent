$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$cryptoJar = Join-Path $env:USERPROFILE '.m2/repository/org/springframework/security/spring-security-crypto/6.3.3/spring-security-crypto-6.3.3.jar'
$loggingJar = Join-Path $env:USERPROFILE '.m2/repository/org/springframework/spring-jcl/6.1.13/spring-jcl-6.1.13.jar'
$classes = Join-Path $projectRoot 'target/classes'
if (!(Test-Path -LiteralPath $cryptoJar) -or !(Test-Path -LiteralPath $loggingJar) -or
    !(Test-Path -LiteralPath (Join-Path $classes 'com/researchagent/security/PasswordHashTool.class'))) {
    throw 'First run mvn -DskipTests compile from the project folder (default Maven cache required).'
}
& java -cp "$classes;$cryptoJar;$loggingJar" com.researchagent.security.PasswordHashTool
if ($LASTEXITCODE -ne 0) { throw 'Password hash generation failed.' }
