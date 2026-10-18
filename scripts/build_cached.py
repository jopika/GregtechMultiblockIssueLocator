#!/usr/bin/env python3
"""Compile, test, and package with pinned cached inputs, without downloads or sockets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def one(cache, pattern):
    paths = sorted(cache.glob(pattern))
    if len(paths) != 1:
        raise SystemExit(f'Expected one cached {pattern}; found {len(paths)}')
    return paths[0]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--mappings', type=Path, required=True)
    args = parser.parse_args()
    java_home, cache, mappings = (p.resolve() for p in (args.java_home, args.cache, args.mappings))
    if not mappings.is_file():
        raise SystemExit('Missing official-to-SRG mappings')
    subprocess.run([sys.executable, str(ROOT / 'scripts/prepare_dependencies.py'), '--check'], check=True)
    sources = sorted((ROOT / 'src/main/java').rglob('*.java'))
    forge = one(cache, 'forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.20_mapped_official_1.20.1/forge-*_mapped_official_1.20.1.jar')
    libraries = [forge, *sorted((cache / 'modules-2/files-2.1').rglob('*.jar'))]
    tool = one(cache, 'forge_gradle/maven_downloader/net/minecraftforge/ForgeAutoRenamingTool/*/*-all.jar')
    production_forge = one(cache, 'forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.20/forge-*-srg.jar')
    if len(libraries) < 2:
        raise SystemExit('Missing cached library inputs')
    build = ROOT / 'build/cached'
    shutil.rmtree(build, ignore_errors=True)
    classes = build / 'classes'
    classes.mkdir(parents=True)
    # Reverse official->SRG for compile-only copies. Installed/ignored libs stay intact.
    mapped_dir = ROOT / 'build/mapped-dependencies'
    mapped_dir.mkdir(parents=True, exist_ok=True)
    expected = json.loads((ROOT / 'scripts/dependencies.json').read_text())['artifacts']
    raw_dependencies = [ROOT / 'libs' / item['file'] for item in expected]
    mapped_dependencies = []
    for raw in raw_dependencies:
        fingerprint = hashlib.sha256((sha(raw) + sha(mappings) + sha(tool)).encode()).hexdigest()
        mapped = mapped_dir / (raw.stem + '-' + fingerprint[:16] + '.jar')
        if not mapped.is_file():
            command = [str(java_home / 'bin/java'), '-jar', str(tool), '--input', str(raw),
                       '--output', str(mapped), '--names', str(mappings), '--reverse', '--threads', '2',
                       '--lib', str(production_forge)]
            for library in [*raw_dependencies, *libraries[1:]]:
                command += ['--lib', str(library)]
            with (build / (raw.stem + '-deobfuscation.log')).open('w') as log:
                subprocess.run(command, check=True, stdout=log, stderr=subprocess.STDOUT)
        mapped_dependencies.append(mapped)
    libraries = [forge, *mapped_dependencies, *libraries[1:]]
    classpath = os.pathsep.join(map(str, libraries))
    # Confirm the remapper changed vanilla calls in LDLib, rather than only copying a JAR.
    panel = subprocess.run([str(java_home / 'bin/javap'), '-c', '-p', '-classpath', str(mapped_dependencies[1]),
                            'com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget'],
                           check=True, capture_output=True, text=True).stdout
    assert 'Minecraft.getInstance:' in panel and 'Minecraft.m_91087_:' not in panel
    processor = one(cache, 'modules-2/files-2.1/org.spongepowered/mixin/0.8.5/*/mixin-0.8.5-processor.jar')
    refmap = classes / 'gtmultiblocklocator.refmap.json'
    subprocess.run([str(java_home / 'bin/javac'), '--release', '17', '-encoding', 'UTF-8',
                    '-cp', classpath, '-processorpath', str(processor) + os.pathsep + classpath,
                    '-processor', 'org.spongepowered.tools.obfuscation.MixinObfuscationProcessorInjection,org.spongepowered.tools.obfuscation.MixinObfuscationProcessorTargets',
                    '-AoutRefMapFile=' + str(refmap), '-d', str(classes), *map(str, sources)], check=True)
    assert refmap.is_file(), 'Mixin annotation processor did not generate a refmap'
    # All selectors are non-obfuscated LDLib members: an empty GENERATED refmap is expected.
    assert json.loads(refmap.read_text()) == {'mappings': {}, 'data': {}}
    test_classes = build / 'test-classes'
    test_classes.mkdir()
    tests = sorted((ROOT / 'src/test/java').rglob('*.java'))
    test_cp = str(classes) + os.pathsep + classpath
    subprocess.run([str(java_home / 'bin/javac'), '--release', '17', '-proc:none', '-cp', test_cp,
                    '-d', str(test_classes), *map(str, tests)], check=True)
    subprocess.run([str(java_home / 'bin/java'), '-cp', str(test_classes) + os.pathsep + test_cp,
                    'io.github.jopika.gtmultiblocklocator.client.FeatureChecks'], check=True)

    dev_jar = build / 'gtmultiblocklocator-dev.jar'
    with zipfile.ZipFile(dev_jar, 'w', zipfile.ZIP_DEFLATED) as jar:
        jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\nMixinConfigs: gtmultiblocklocator.mixins.json\r\n\r\n')
        for directory in (classes, ROOT / 'src/main/resources'):
            for path in sorted(directory.rglob('*')):
                if path.is_file():
                    jar.write(path, str(path.relative_to(directory)))
        jar.write(ROOT / 'LICENSE', 'LICENSE')
    output = ROOT / 'build/libs/gtmultiblocklocator-1.0.0.jar'
    output.parent.mkdir(parents=True, exist_ok=True)
    command = [str(java_home / 'bin/java'), '-jar', str(tool), '--input', str(dev_jar), '--output', str(output),
               '--names', str(mappings), '--threads', '2']
    for library in libraries:
        command += ['--lib', str(library)]
    with (build / 'reobfuscation.log').open('w') as log:
        subprocess.run(command, check=True, stdout=log, stderr=subprocess.STDOUT)
    verifier_classes = build / 'verifier-classes'
    verifier_classes.mkdir()
    subprocess.run([str(java_home / 'bin/javac'), '--release', '17', '-proc:none', '-cp', classpath,
                    '-d', str(verifier_classes), str(ROOT / 'scripts/CheckLinkage.java')], check=True)
    subprocess.run([str(java_home / 'bin/java'), '-cp', str(verifier_classes) + os.pathsep + classpath,
                    'CheckLinkage', str(output), str(production_forge),
                    *map(str, raw_dependencies), *map(str, libraries[3:])], check=True)
    (build / 'build-provenance.json').write_text(json.dumps({
        'method': 'cached dependency deobfuscation + javac/Mixin AP + feature checks + ForgeAutoRenamingTool',
        'java_home': str(java_home), 'forge_sha256': sha(forge), 'mappings_sha256': sha(mappings),
        'remapper_sha256': sha(tool), 'jar_sha256': sha(output),
        'mixin_processor_sha256': sha(processor), 'refmap_sha256': sha(refmap),
        'compile_inputs': {raw.name: {'raw_sha256': sha(raw), 'mapped_sha256': sha(mapped)}
                           for raw, mapped in zip(raw_dependencies, mapped_dependencies)},
    }, indent=2) + '\n')
    subprocess.run([sys.executable, str(ROOT / 'scripts/verify_artifact.py'), str(output),
                    '--java-home', str(java_home)], check=True)
    print(f'Built: {output}')

if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as failure:
        raise SystemExit(f'Build step failed (exit {failure.returncode}); see diagnostic above.') from None
