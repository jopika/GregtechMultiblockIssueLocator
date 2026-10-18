# GregTech Multiblock Locator

A client utility mod built for Star Technology that enables players to find errors with GregTech multiblocks quicker by highlighting problematic blocks.


## How to use
For contollers that show the coordinates for an issue, they will now be highlighted in cyan.

Click on the coordinates to highlight the block's position (visible through the wall). The outline will vanish after a brief amount of time (default 5 seconds).

## Installation

1. Download the mod jar file from the releases tab.
1. Make sure Minecraft is closed, and place the jar file in the `mods` directory.

## Configuration

There are multiple ways to configure the mod, the easiest way is via ModConfig in game.

Alternatively, after the first client startup, edit `config/gtmultiblocklocator-client.toml`
and restart the client:

```toml
[highlight]
enabled = true
durationSeconds = 5
```

- `enabled`: enables the coordinate links and outline. Disabling clears existing cues.
- `durationSeconds`: whole seconds from **1 to 300**, measured from GUI close.
  Each click uses the duration configured when the position is selected.

## Coverage and limitations

The addon highlights only the one error that was being reported at a time.
It also doesn't update as you repair blocks, so they can get stale pretty quickly.

## License

[MIT](LICENSE).
