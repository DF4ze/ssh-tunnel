# ─────────────────────────────────────────────────────────────────────────────
# gateway-watcher.ps1
# Surveillance du fichier trigger pour redémarrage automatique du gateway.
#
# Démarrage : via launch-watcher.vbs (dans le dossier Startup Windows)
# Trigger   : D:\Installs\Ssh-tunnel\restart.trigger
# Action    : tue java.exe sur le port 8765, supprime le trigger, relance le VBS
# ─────────────────────────────────────────────────────────────────────────────

$triggerFile = "D:\Installs\Ssh-tunnel\restart.trigger"
$gatewayVbs  = "D:\Installs\Ssh-tunnel\launch.vbs"
$gatewayPort = 8765
$pollInterval = 2  # secondes entre chaque vérification

Write-Host "$(Get-Date -Format 'HH:mm:ss') Gateway Watcher démarré."
Write-Host "  Trigger : $triggerFile"
Write-Host "  Polling toutes les $pollInterval secondes..."

while ($true) {
    if (Test-Path $triggerFile) {
        Write-Host "$(Get-Date -Format 'HH:mm:ss') Trigger détecté — redémarrage du gateway..."

        # Supprimer le trigger AVANT de tuer le process (évite une boucle si le restart échoue)
        Remove-Item $triggerFile -Force -ErrorAction SilentlyContinue

        # Trouver le PID écoutant sur le port du gateway
        $netstatLine = netstat -ano | Select-String ":$gatewayPort\s" | Select-String "LISTENING"
        if ($netstatLine) {
            $pid = ($netstatLine -split "\s+")[-1].Trim()
            Write-Host "$(Get-Date -Format 'HH:mm:ss') Arrêt du process PID $pid..."
            Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 2
        } else {
            Write-Host "$(Get-Date -Format 'HH:mm:ss') Aucun process trouvé sur le port $gatewayPort."
        }

        # Relancer le gateway via le VBS (fenêtre cachée)
        Start-Process "wscript.exe" -ArgumentList "`"$gatewayVbs`""
        Write-Host "$(Get-Date -Format 'HH:mm:ss') Gateway relancé."
    }

    Start-Sleep -Seconds $pollInterval
}
