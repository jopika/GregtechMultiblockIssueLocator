# GregTech Multiblock Locator

A client utility mod built for Star Technology that enables players to find errors with GregTech multiblocks quicker by highlighting problematic blocks.


## How to use

1. Open the multiblock controller. When it reports an issue with coordinates, those coordinates appear as a cyan link.

   <img src="docs/images/controller-error-coordinates.png" alt="Large Chemical Reactor controller reporting an invalid structure with clickable cyan error coordinates" width="800">

2. Click the coordinates, then close the controller screen.  A red outline marks the reported block position, visible through the multiblock's casing.  
By default the outline will last 5 seconds after closing the GUI.

   <img src="docs/images/highlight-through-casing.png" alt="Red outline marking the reported block position through the reactor casing" width="800">
    
    Top view of the reactor showing the block highlighted in the middle.

   <img src="docs/images/interior-error-highlighted.png" alt="Red outline identifying the reported block position inside the reactor" width="800">

    Looking inside we can see that the block is the wrong type (should be a PTFE Pipe block).

   <img src="docs/images/interior-error-before-highlight.png" alt="Interior of the reactor before highlighting, with the error position difficult to distinguish" width="800">

After making a repair, check the controller again for any remaining errors and click the updated coordinates if there is a new error.

## Installation

This has been built and is tested on the Star Technology Theta 2 version. Other versions should work, but have not been verified!

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
