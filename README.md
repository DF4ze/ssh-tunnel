# SSH Tunnel Gateway

Gateway Spring Boot exposant un serveur MCP (Model Context Protocol) pour permettre à Claude (Cowork) de gérer le serveur distant et les builds locaux.

## Démarrage

Le gateway se lance via `launch.vbs` (dans le dossier Startup Windows pour un démarrage automatique à la session).

```
D:\Installs\Ssh-tunnel\
├── launch.vbs             → démarre le gateway (avec systray), lancé au démarrage Windows
├── startTunnels.bat       → lancé par launch.vbs, boucle de restart intégrée
├── ssh-tunnel-gateway.jar → JAR déployé (nom stable, sans version)
├── application.properties → configuration complète
├── playbook.json          → opérations CI/CD par projet
├── local-safe.json        → commandes locales autorisées (PERMANENT)
└── logs/
    ├── stdout.log         → sortie standard du gateway
    └── stderr.log         → erreurs du gateway
```

## Fonctionnalités MCP

### Statut & connexion SSH

| Tool | Description |
|------|-------------|
| `getStatus()` | Statut global : connexion SSH, tunnels actifs, services gérables |
| `reconnect()` | Force la reconnexion SSH si la connexion est tombée |
| `disconnect()` | Déconnecte manuellement (suspend la reconnexion automatique) |

### Services systemd (serveur distant)

| Tool | Description |
|------|-------------|
| `getAllServicesStatus()` | État de tous les services whitelist en une passe |
| `getServiceLogs(name, lines)` | `journalctl` — N dernières lignes |

Les services autorisés sont définis dans `application.properties` (`gateway.managed-services`).

### Accès local (machine Windows)

| Tool | Description |
|------|-------------|
| `readLocalFile(path)` | Lecture d'un fichier local (max 512 KB) |
| `listLocalDir(path)` | Listage d'un répertoire local |
| `getLocalSystemInfo()` | CPU, RAM, disques de la machine locale |
| `getLocalSafeList()` | Affiche les commandes/chemins approuvés |
| `execLocalCommand(cmd, scope)` | Exécute une commande locale (workflow de permission) |
| `writeLocalFile(path, content, scope)` | Écrit un fichier local (workflow de permission) |

Les commandes locales nécessitent une approbation : `ONCE`, `SESSION` ou `PERMANENT`.  
Les commandes PERMANENT sont persistées dans `local-safe.json`.

### Transfert de fichiers

| Tool | Description |
|------|-------------|
| `uploadFile(localPath, remotePath)` | Transfère un fichier local → serveur distant via SFTP |

### CI/CD — Playbook

| Tool | Description |
|------|-------------|
| `listOperations()` | Liste les opérations configurées dans `playbook.json` |
| `executeOperation(name)` | Lance une opération en arrière-plan (async) |
| `checkOperation(name, lines)` | Statut + dernières lignes de log |

#### Structure du playbook.json

```json
{
  "operations": {
    "build:mon-projet": {
      "description": "Build Maven local",
      "steps": [
        { "type": "local",  "command": "..." },
        { "type": "remote", "command": "systemctl restart mon-service" },
        { "type": "upload", "localPath": "...", "remotePath": "..." }
      ]
    }
  }
}
```

Types de step : `local` (Windows), `remote` (SSH), `upload` (SFTP).  
La version du JAR est lue automatiquement depuis le `pom.xml` du projet — pas besoin de la hardcoder.

Les logs d'exécution sont écrits dans `C:\temp\gateway-ops\{nom-opération}\output.log`.

### Redémarrage du gateway

| Tool | Description |
|------|-------------|
| `restartGateway()` | Demande un redémarrage via le watcher (connexion MCP coupée ~3s puis rétablie) |

Le redémarrage fonctionne via un fichier trigger (`restart.trigger`) et une boucle dans `startTunnels.bat` :
1. Le gateway écrit `restart.trigger` puis appelle `System.exit(0)`
2. Le bat détecte le trigger, le supprime, et relance le JAR automatiquement

Aucun processus externe requis — la boucle est intégrée au lanceur.

## Configuration (application.properties)

```properties
# SSH
ssh.host=...
ssh.port=22
ssh.user=...
ssh.password=...
ssh.check-interval=10          # secondes entre chaque vérif de connexion

# Tunnels port-forwarding
ssh.tunnels[0].local-port=3307
ssh.tunnels[0].remote-host=127.0.0.1
ssh.tunnels[0].remote-port=3306

# Gateway
gateway.managed-services=tor,mariadb
gateway.sudo-password=...
gateway.restart-trigger-path=D:\\Installs\\Ssh-tunnel\\restart.trigger

# Accès local
local.safe-list-path=D:\\Installs\\Ssh-tunnel\\local-safe.json

# CI/CD Playbook
playbook.path=D:\\Installs\\Ssh-tunnel\\playbook.json
playbook.output-dir=C:\\temp\\gateway-ops
```

## Versioning

| Chiffre | Quand |
|---------|-------|
| X._._ | Refonte complète (jamais touché en évolution normale) |
| _.X._ | Nouvelle feature ou amélioration |
| _._.X | Correction de bug |

Versions :
- `1.1.0` — version initiale (SSH tunnels + MCP + accès local)
- `1.2.0` — CI/CD Playbook (executeOperation, checkOperation, listOperations)
- `1.3.0` — Redémarrage gateway via watcher (restartGateway)

## Déploiement

```bash
# Build
mvn clean package -DskipTests

# Déploiement local (via playbook)
executeOperation("deploy:ssh-tunnel-gateway")

# Puis redémarrer le gateway
restartGateway()
```
