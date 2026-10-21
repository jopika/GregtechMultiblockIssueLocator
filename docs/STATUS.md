# Current status

Version: 1.0.1. This patch tests the tagged release build and automatic JAR
attachment; it contains no gameplay changes.

Local artifact: `build/libs/gtmultiblocklocator-1.0.1.jar`.
The documented cached builder compiled with Java 17, passed 55 feature checks,
resolved 286 production method/field references, and verified production JAR
metadata, mappings, mixin packaging, and dependency exclusions.

GitHub Actions release build and automatic JAR/checksum attachment will be checked
after pushing tag `1.0.1`.

Runtime mixin application, rendering, and server compatibility remain untested.
Next runtime check: launch with the pinned pack and verify clicking a multiblock
error position highlights the expected block. No instance was modified or launched.
