# Adds *.pawtrack.local aliases to the Windows hosts file (run as Administrator).
# Needed so the browser and the containers see the same Keycloak issuer URL.
$hostsFile = "$env:SystemRoot\System32\drivers\etc\hosts"
$names = @(
  "keycloak.pawtrack.local",
  "gateway.pawtrack.local",
  "identity.pawtrack.local",
  "pet.pawtrack.local",
  "health.pawtrack.local",
  "notification.pawtrack.local"
)
$current = Get-Content $hostsFile -ErrorAction Stop
foreach ($name in $names) {
  if (-not ($current -match "\s$([regex]::Escape($name))(\s|$)")) {
    Add-Content -Path $hostsFile -Value "127.0.0.1`t$name"
    Write-Host "added $name"
  } else {
    Write-Host "exists $name"
  }
}
