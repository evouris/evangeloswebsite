# Sets the username/password for the private pages (tasks, calendar, managing projects).
# The password is never saved: only its BCrypt hash goes into config/application.properties (git-ignored).
#
# Run from the project folder (works from cmd or PowerShell):
#   powershell -ExecutionPolicy Bypass -File .\scripts\set-password.ps1
# Then restart the server.

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$configFile = Join-Path $root 'config\application.properties'
$m2 = Join-Path $env:USERPROFILE '.m2\repository'

# BCrypt comes from Spring Security's crypto library, already downloaded by Maven.
function Find-Jar($dir, $name) {
	$path = Join-Path $m2 $dir
	if (-not (Test-Path $path)) { return $null }
	Get-ChildItem $path -Recurse -Filter "$name-*.jar" |
		Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
		Sort-Object LastWriteTime -Descending |
		Select-Object -First 1 -ExpandProperty FullName
}
$jars = @(
	(Find-Jar 'org\springframework\security\spring-security-crypto' 'spring-security-crypto'),
	(Find-Jar 'org\springframework\spring-core' 'spring-core'),
	(Find-Jar 'commons-logging\commons-logging' 'commons-logging')
)
if ($jars -contains $null) {
	Write-Host "Couldn't find the Spring Security libraries. Run '.\mvnw.cmd compile' once, then try again." -ForegroundColor Red
	exit 1
}
$java = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { "$env:JAVA_HOME\bin\java.exe" } else { 'java' }

$user = Read-Host "Username (press Enter to keep 'evangelos')"
if ([string]::IsNullOrWhiteSpace($user)) { $user = 'evangelos' }

function Read-Secret($prompt) {
	$secure = Read-Host $prompt -AsSecureString
	$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
	try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
	finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
$password = Read-Secret 'New password (typing is hidden)'
if ($password.Length -lt 8) {
	Write-Host 'Use at least 8 characters. Nothing was changed.' -ForegroundColor Red
	exit 1
}
if ($password -ne (Read-Secret 'Type it again')) {
	Write-Host "Passwords didn't match. Nothing was changed." -ForegroundColor Red
	exit 1
}

# Hash it with a tiny one-off Java program. The password goes in through stdin, not the command line.
$hasher = Join-Path $env:TEMP 'HashPassword.java'
Set-Content -Path $hasher -Encoding ascii -Value @'
public class HashPassword {
	public static void main(String[] args) {
		String password = new java.util.Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8).nextLine();
		// Windows PowerShell puts an invisible byte-order mark in front of piped text; drop it.
		if (!password.isEmpty() && password.charAt(0) == 0xFEFF) {
			password = password.substring(1);
		}
		System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));
	}
}
'@
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
try {
	$hash = ($password | & $java -cp ($jars -join ';') $hasher | Select-Object -Last 1).Trim()
} finally {
	Remove-Item $hasher -ErrorAction SilentlyContinue
	$password = $null
}
if ($hash -notmatch '^\$2[aby]\$\d\d\$') {
	Write-Host "Hashing failed, so nothing was changed. Output: $hash" -ForegroundColor Red
	exit 1
}

# Replace any old login lines and keep everything else in the file.
$lines = @()
if (Test-Path $configFile) {
	$lines = @(Get-Content $configFile | Where-Object { $_ -notmatch '^\s*spring\.security\.user\.(name|password)\s*=' })
} else {
	New-Item -ItemType Directory -Force (Split-Path $configFile) | Out-Null
	$lines = @('# Local secrets: this folder is in .gitignore, so it never goes to GitHub.')
}
$lines += 'spring.security.user.name=' + $user
$lines += 'spring.security.user.password={bcrypt}' + $hash
[IO.File]::WriteAllLines($configFile, [string[]]$lines, (New-Object System.Text.UTF8Encoding $false))

Write-Host "Saved. Username: $user. Restart the server to use the new password." -ForegroundColor Green
