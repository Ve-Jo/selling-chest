# SellingChest

`SellingChest` is a Bukkit/Purpur plugin that turns containers into player-facing shop chests.

It was originally built for **IridiumSkyblock** islands, but it can also fall back to a more generic mode when IridiumSkyblock is not available.

## Features

- Create trading chests with an admin wand via `/sellchest`
- Supports `CHEST`, `TRAPPED_CHEST`, `BARREL`, and shulker boxes
- Owner setup flow for configuring item, price, display name, and enabled state
- Guest purchase flow backed by Vault economy
- FancyHolograms support for live text + item displays above the chest
- Periodic hologram refresh showing current stock and price
- Restricts stock to the configured selling item only
- Auto-removes registered selling chests when the container is broken or exploded
- Optional IridiumSkyblock integration to restrict creation to island members and group chests per island

## Requirements

- Minecraft server software compatible with Bukkit/Paper/Purpur APIs
- Java 21 for building this project
- `Vault` and a Vault-compatible economy plugin
- `FancyHolograms`
- Optional: `IridiumSkyblock`

## Installation

1. Build the plugin jar.
2. Install `Vault` and your economy provider.
3. Install `FancyHolograms`.
4. Optionally install `IridiumSkyblock` for island-aware restrictions.
5. Put the `SellingChest` jar into your server `plugins` folder.
6. Start the server once to generate `config.yml`.
7. Adjust config values as needed and restart/reload the server.

## Building

```bash
./gradlew build
```

## Command

### Available command usage

- `/sellchest`
  - Gives the selling chest wand
- `/sellchest market`
  - Opens the auction/market menu if `selling-chests.auction-menu.enabled` is turned on

## Permission

- `sellingchest.admin`
  - Allows creating and managing selling chests
  - Default: `op`

## Configuration

Main configuration file:

- `src/main/resources/config.yml`

Example configuration:

```yaml
selling-chests:
  auction-menu:
    enabled: true
    title: '§6§lАукцион торговых сундуков'
    size: 54

  hologram-y-offset: 1.5
  update-interval-seconds: 5

  messages:
    purchase-success: '&aВы купили &f{item} &aза &f${price}&a!'
    purchase-fail-no-money: '&cУ вас недостаточно средств. Цена: &f${price} &c| Ваш баланс: &f${balance}'
    purchase-fail-no-stock: '&cЭтот сундук закончился.'
    purchase-fail-full-inventory: '&cВаш инвентарь заполнен!'
    owner-notification: '&e{buyer} &акупил &f{item} &aот вашего торгового сундука за &f${price}&a!'
    chest-disabled: '&cЭтот сундук отключен.'
    chest-created: '&aТорговый сундук создан! Используйте /sellchest для настройки.'
    chest-removed: '&aТорговый сундук удален!'
    no-permission: '&cУ вас нет разрешения на это.'
```

This config controls the market menu, hologram height, refresh interval, and all player-facing purchase/setup messages.

## How It Works

- An admin uses the wand on an empty supported container.
- If IridiumSkyblock is installed, the plugin verifies the player is a member of the island at that location.
- The chest is registered and can then be configured by the owner.
- Buyers can interact with enabled chests to purchase stock using Vault economy.
- `/sellchest market` opens the paginated public market GUI backed by Vault economy when enabled.
- Holograms show product name, price, stock amount, and interaction hints.

## Technical Notes

- Main class: `SellingChestPlugin`
- Plugin name: `SellingChest`
- Soft dependency: `IridiumSkyblock`
- Hard dependencies: `Vault`, `FancyHolograms`
