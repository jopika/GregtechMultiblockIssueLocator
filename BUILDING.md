# Building and releasing

## Requirements

Use Java 17 (with `java`, `javac`, and `javap` on `PATH`) and Python 3.11 or newer.
The Gradle wrapper downloads Gradle 8.8. The pinned target is Minecraft 1.20.1,
Forge 47.4.20, GregTech CEu StarT 1.7.1c, and LDLib 1.0.52.a.

## Building from source

Download the two pinned mod dependencies and verify their SHA-256 hashes:

```sh
python3 scripts/prepare_dependencies.py --download
./gradlew build
```

Alternatively, copy the matching dependencies from an existing modpack:

```sh
python3 scripts/prepare_dependencies.py '/path/to/instance/mods'
python3 scripts/prepare_dependencies.py --check
./gradlew build
```

Dependency filenames, download URLs, and hashes are recorded in
`scripts/dependencies.json`. Preparation writes only to ignored `libs/`; it does
not modify or launch the source instance. Dependencies are not bundled in the
addon JAR.

The build runs 55 feature checks and verifies production packaging, mappings,
client placement, and dependency exclusions. The production JAR is written to
`build/libs/gtmultiblocklocator-1.0.1.jar`. To verify it separately:

```sh
python3 scripts/verify_artifact.py
```

These checks do not establish in-game rendering, runtime mixin application, or
server handshake compatibility.

## Cached offline builds

When a prepared ForgeGradle cache and official-to-SRG mappings are available,
the cached builder can compile and package without downloads or Gradle daemon
sockets:

```sh
python3 scripts/build_cached.py --java-home /path/to/jdk17 --cache /path/to/gradle/caches --mappings /path/to/output.tsrg
python3 scripts/verify_artifact.py --java-home /path/to/jdk17
```

In the shared development workspace, resolve those paths with
`python3 ../tooling/scripts/preflight.py --project .`. The cached builder also
checks production method and field linkage against the pinned dependencies.

## Release builds

Pushing a version tag such as `1.0.1` or `v1.0.1` runs the **Build release JAR**
GitHub Actions workflow. Before tagging, update the version in `build.gradle`,
`src/main/resources/META-INF/mods.toml`, and the default JAR path and version check
in `scripts/verify_artifact.py` together.

The workflow downloads the two pinned compile inputs from CurseForge, verifies
their SHA-256 hashes, builds with Java 17, runs the feature checks, and verifies
production packaging. For tag builds, it attaches the JAR and `SHA256SUMS` to the
matching GitHub Release, creating a release with generated notes if one does not
exist. Rerunning a tag build replaces assets with the same filenames and retains
existing release notes.

The JAR and checksum are also available in the workflow run's artifact. The
workflow can be run manually from the Actions tab: select a version tag to
publish release assets, or a branch to build an artifact without publishing a
release. Publishing uses the workflow's `GITHUB_TOKEN` with `contents: write`;
no additional secret is required.

CI dependency preparation can be run locally with:

```sh
python3 scripts/prepare_dependencies.py --download
```
