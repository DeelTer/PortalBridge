# PortalBridge

A Paper 1.21.3+ plugin for creating beautiful cross-server portals with advanced server detection and player consent management.

## Features

### Portal Mechanics
- **Interactive Portals**: Right-click animated door entities to browse nearby servers and transfer players
- **Visual Design**: BlockDisplay door with smooth opening/closing animation and holographic information display
- **Countdown Timer**: Color-coded remaining lifetime (green → yellow → red based on configurable thresholds)
- **Holographic Display**: Real-time MiniMessage formatted server info including player count, version, and online-mode status

### Server Detection & Flags
- **Live Server Pinging**: Fetches server status from mcsrvstat.us API + raw Minecraft SLP protocol
- **Plugin Detection**: Automatically detects installed plugins (auth systems, anti-cheat, proxies, etc.)
- **Server Flags**: Encodes server capabilities in JSON response field for downstream detection
- **Caching**: Caffeine-based response caching with configurable TTL per portal lifetime

### Trust & Consent System
- **Whitelist/Blacklist**: Trust lists with enable/disable per list type
- **Untrusted Server Warnings**: Alerts players when transferring to servers outside trust lists
- **Consent Caching**: Remembers player consent per server (configurable 1-hour default)
- **Cooldown**: Brief cooldown on repeated warnings to prevent spam

### Commands
```
/portal open <host:port>          - Create a portal to the target server
/portal <host:port>               - Shorthand syntax (legacy support)
/portal hub|lobby                 - Transfer directly to configured hub server
/portal bind <host:port>          - Bind current held door item to a server (requires portalbridge.bind)
/portal force <host:port>         - Grant transfer consent and transfer immediately (requires explicit command)
/portal admin reload              - Reload configuration (requires portalbridge.admin)
/portal admin removeall           - Remove all active portals (requires portalbridge.admin)
/portal help                      - Show help message
```

### Permissions
- `portalbridge.command.open` - Use `/portal open` and legacy syntax
- `portalbridge.bind` - Bind doors to servers via `/portal bind`
- `portalbridge.force` - Use `/portal force` (bypass untrusted warning)
- `portalbridge.admin` - Use admin subcommands

## Configuration

### Main Config (config.yml)

All values are documented inline. Key sections:

#### Hub Server
```yaml
hub:
  host: "cominers.net"
  port: 25565
```
Default destination for `/portal hub` and `/portal lobby` commands.

#### Portal Behavior
```yaml
portal:
  lifetime-seconds: 30              # How long portals remain before auto-expiring
  collision-radius: 5.0             # Minimum distance between portals (blocks)
  player-proximity-block-radius: 2.0 # Distance to prevent placing portals near players
  max-portals-per-player: 1         # Maximum concurrent portals per player
  max-placement-distance: 6         # Maximum distance player can look to place portal (blocks)
```

#### Hologram Display
```yaml
portal:
  hologram:
    height: 3.4                     # Hologram Y offset above portal
    format: "<gold><bold><motd>...</format>"
    format-unreached: "<red><bold><host>...</format-unreached>"
    timer:
      green-threshold: 50           # % of lifetime where timer is green
      yellow-threshold: 25          # % of lifetime where timer is yellow (below green)
                                    # Below yellow: red
```

**Hologram Format Placeholders:**
- `<motd>` - Server MOTD (Minecraft-formatted text)
- `<online>` - Current player count
- `<max>` - Max player count
- `<version>` - Server version string
- `<host>` - Server hostname
- `<port>` - Server port
- `<player>` - Portal owner name
- `<time>` or `<colored_time>` - Remaining seconds with auto color coding

#### Animation & Sound
```yaml
portal:
  animation:
    open-ticks: 5                   # Duration of door open animation (ticks)
    shrink-ticks: 6                 # Duration of portal shrink-out animation (ticks)
  auto-close-ticks: 100             # Ticks before portal auto-closes after player enters
  notify-radius: 12.0               # Radius for action-bar notifications when player enters
```

#### Cache Settings
```yaml
cache:
  ping-ttl-seconds: 120             # How long to cache mcsrvstat API responses (seconds)
  max-size: 100                     # Maximum cached server responses
ping-timeout-millis: 5000           # Timeout for server pinging (ms)
portal-check-radius: 2.5            # Distance to check for players in portal interaction
```

#### Trust Lists
```yaml
trust-lists:
  whitelist:
    enabled: false
    entries:
      - "trusted.server.com:25565"
  blacklist:
    enabled: false
    entries:
      - "blocked.server.com"
```

When whitelist is enabled, only whitelisted servers appear in portals.
When blacklist is enabled, blacklisted servers cannot be accessed.

**Entry Format:** `host` or `host:port` (port 25565 if omitted).

#### Consent System
```yaml
consent:
  ttl-seconds: 3600                 # How long to remember player consent (1 hour)
  cooldown-seconds: 15              # Cooldown between untrusted warnings
command-cooldown-seconds: 2         # Cooldown between /portal open commands
```

#### Server Detection
```yaml
require-portalbridge-flag: true     # Require target server to have PortalBridge installed
require-accept-transfers: false     # Require server.properties: accepts-transfers=true
```

#### Language & Debug
```yaml
language:
  default: "en"                     # Default language (en, ru supported)
  auto-detect: true                 # Auto-detect player locale

debug: false                         # Enable verbose logging
```

## Architecture

### Core Classes

#### Portal Management
- **`Portal`** - Data model for active portal with location, target server, entities, timers
- **`PortalManager`** - Creates/removes/manages all active portals, handles collisions and limits
- **`PortalListener`** - Handles player interaction with portals, transfer logic, animations

#### Commands
- **`PortalCommand`** - Command executor for all `/portal` subcommands and tab completion

#### Server Detection & Pinging
- **`ServerPinger`** - Async parallel pinger combining mcsrvstat.us API + raw SLP protocol
- **`McsrvstatPinger`** - HTTP client for mcsrvstat.us API (player count, version, MOTD)
- **`MinecraftPinger`** - Raw Minecraft SLP protocol handler (extracts server flags)
- **`ServerInfo`** - Data model for server status response (players, version, flags, uptime)

#### Flag System
- **`ServerFlag`** - Enum of detectable server capabilities (ONLINE_MODE, AUTH, PROXY, etc.)
- **`FlagDetector`** - Detects plugins and features on local server
- **`PortalBridgeFlagModifier`** - Injects flags into server status JSON response (new packet modifier system)
- **`FlagInjectorLegacy`** - Legacy fallback flag injection via MOTD invisible characters

#### Configuration & Utilities
- **`ConfigManager`** - Loads all configuration values with defaults
- **`ConsentCache`** - Caffeine-based caching for player consent and cooldowns
- **`TrustListManager`** - Evaluates whitelist/blacklist rules
- **`DoorBindManager`** - Stores per-item server bindings (NBT-based)

#### UI & Display
- **`PortalDisplayUpdater`** - Renders hologram text with MiniMessage formatting
- **`DoorAnimator`** - Calculates door rotation transforms for opening/closing animation

### Data Flow

```
Player right-clicks door entity
  ↓
PortalListener.onEntityInteract()
  ├─ Check if portal is open
  ├─ Fetch cached ServerInfo or async ping via ServerPinger
  ├─ Validate: server accepting transfers + has PortalBridge (if required)
  ├─ Check whitelist/blacklist
  ├─ Request consent if untrusted + auth required
  └─ Animate door open → transfer player → auto-close after delay

/portal open <host:port>
  ↓
PortalCommand.handleOpen()
  ├─ Ray trace for block under player view
  ├─ Check placement collisions + distance rules
  ├─ Create Portal entity + hologram + interaction zone
  ├─ Async ping via ServerPinger
  └─ Update hologram with live server data
```

## API Usage

### Listening to Portal Events

Extend `PortalListener` or listen to Bukkit's `PlayerInteractEntityEvent`:

```java
@EventHandler
public void onPortalClick(PlayerInteractEntityEvent event) {
    Portal portal = plugin.getPortalManager().getPortalByEntity(event.getRightClicked());
    if (portal != null) {
        // Handle portal interaction
    }
}
```

### Programmatically Creating Portals

```java
PortalManager manager = plugin.getPortalManager();
Player player = ...; // target player
Portal portal = manager.createPortal(
    player, "play.myserver.com", 25565,
    30,  // lifetime in seconds
    Material.OAK_DOOR,  // optional custom door material (null for config default)
    null // optional pre-cached ServerInfo (null to fetch async)
);
if (portal != null) {
    manager.startPingAndUpdateHologram(portal, 30);
}
```

### Accessing Server Info Cache

```java
ServerPinger pinger = plugin.getServerPinger();
pinger.ping("play.myserver.com", 25565).thenAccept(info -> {
    if (info.isOnline()) {
        int players = info.getOnline();
        String version = info.getVersion();
    }
});
```

## Requirements

- **Minecraft**: 1.21.3+ (Paper 1.21.3-R0.1 or later)
- **Java**: 21+
- **Paperweight**: Uses `paperweight-userdev` for NMS packet injection

## Building

```bash
./gradlew build
```

Output: `build/libs/PortalBridge-*.jar`

## Installation

1. Place `PortalBridge.jar` in `plugins/` folder
2. Restart server
3. Edit `plugins/PortalBridge/config.yml` and `lang/en.yml` or `lang/ru.yml`
4. Reload with `/portal admin reload`

## Known Limitations

- Portals expire after configured lifetime (configurable per portal via command)
- Flag injection requires NMS packet modifier support (falls back to legacy MOTD injection if unavailable)
- mcsrvstat.us API required for player count & version (unreachable servers shown as `?`)

## License

Proprietary. All rights reserved.

## Support

For issues, configuration help, or feature requests, contact the author.
