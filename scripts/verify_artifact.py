#!/usr/bin/env python3
"""Check production packaging, side placement, pinned mixin selectors and mappings."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'io/github/jopika/gtmultiblocklocator/'
ENTRY = PACKAGE + 'GregTechMultiblockLocator.class'
CONFIG = 'gtmultiblocklocator.mixins.json'
REFMAP = 'gtmultiblocklocator.refmap.json'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path, nargs='?', default=ROOT / 'build/libs/gtmultiblocklocator-1.0.0.jar')
    parser.add_argument('--java-home', type=Path)
    args = parser.parse_args()
    javap = str(args.java_home / 'bin/javap') if args.java_home else 'javap'

    def inspect(classpath, *classes, verbose=False):
        return subprocess.run([javap, '-p', '-c', *(['-v'] if verbose else []), '-classpath', str(classpath),
                               *classes], check=True, capture_output=True, text=True).stdout

    with zipfile.ZipFile(args.jar) as jar:
        names = jar.namelist()
        assert len(names) == len(set(names)), 'Duplicate JAR entries'
        assert {'META-INF/mods.toml', 'META-INF/MANIFEST.MF', 'pack.mcmeta', 'LICENSE', ENTRY, CONFIG, REFMAP} <= set(names)
        assert json.loads(jar.read('pack.mcmeta'))['pack']['pack_format'] == 15
        metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode())
        assert metadata['mods'][0]['modId'] == 'gtmultiblocklocator'
        assert metadata['mods'][0]['version'] == '1.0.0'
        assert metadata['mods'][0]['displayTest'] == 'IGNORE_ALL_VERSION'
        dependencies = metadata['dependencies']['gtmultiblocklocator']
        assert {d['modId'] for d in dependencies} == {'minecraft', 'forge', 'gtceu', 'ldlib'}
        assert all(d['side'] == 'CLIENT' for d in dependencies if d['modId'] in ('gtceu', 'ldlib'))
        assert 'MixinConfigs: ' + CONFIG in jar.read('META-INF/MANIFEST.MF').decode()
        config = json.loads(jar.read(CONFIG))
        assert config['required'] and config['refmap'] == REFMAP
        assert config['client'] == ['ComponentPanelWidgetMixin']
        assert not config.get('mixins') and not config.get('server'), 'Common/server mixins present'
        assert config['injectors']['defaultRequire'] == 1
        # All selectors target stable, non-obfuscated LDLib names with remap=false.
        assert json.loads(jar.read(REFMAP)) == {'mappings': {}, 'data': {}}
        assert json.loads(jar.read('assets/gtmultiblocklocator/lang/en_us.json'))['gtmultiblocklocator.highlight'] == 'Click to highlight position'
        classes = [n for n in names if n.endswith('.class')]
        assert all(n.startswith(PACKAGE) for n in classes), 'Bundled dependency classes'
        assert not any('FeatureChecks' in n or '/test/' in n for n in names), 'Bundled tests'
        for name in classes:
            assert name == ENTRY or name.startswith((PACKAGE + 'client/', PACKAGE + 'mixin/')), name
            data = jar.read(name)
            assert b'appeng/' not in data and b'expandedae' not in data, 'Unwanted dependency reference'
        entry = jar.read(ENTRY)
        assert all(token not in entry for token in (b'net/minecraft/client/', b'com/lowdragmc/', b'com/gregtechceu/'))

    class_names = [n[:-6].replace('/', '.') for n in classes]
    bytecode = inspect(args.jar, *class_names, verbose=True)
    common = inspect(args.jar, 'io.github.jopika.gtmultiblocklocator.GregTechMultiblockLocator', verbose=True)
    assert 'DistExecutor.safeRunWhenOn:' in common and 'CLIENT' in common, 'Client config registration must be side-gated'
    config_code = inspect(args.jar, 'io.github.jopika.gtmultiblocklocator.client.ClientConfig', verbose=True)
    assert 'ModConfig$Type.CLIENT:' in config_code and 'ModLoadingContext.registerConfig:' in config_code
    assert 'major version: 61' in bytecode
    # These vanilla calls are used by the observable path and MUST be SRG in production.
    assert 'net/minecraft/client/Minecraft.m_91087_:' in bytecode, 'Minecraft.getInstance was not remapped'
    assert 'net/minecraft/client/Minecraft.f_91073_:' in bytecode, 'Minecraft.level was not remapped'
    assert 'net/minecraft/network/chat/Component.m_237115_:' in bytecode, 'Component.translatable was not remapped'
    for owner, member in [('Minecraft', 'getInstance'), ('Minecraft', 'level'), ('Component', 'getContents'),
                          ('Component', 'getStyle'), ('Component', 'translatable'), ('BlockPos', 'getX')]:
        assert not re.search(r'net/minecraft/[\w/$]+/' + owner + r'\.' + member + r':', bytecode), (owner, member)
    mixin = inspect(args.jar, 'io.github.jopika.gtmultiblocklocator.mixin.ComponentPanelWidgetMixin', verbose=True)
    for selector in ('formatDisplayText()V', 'updateScreen()V', 'mouseClicked(DDI)Z',
                     'Lcom/lowdragmc/lowdraglib/gui/widget/ComponentPanelWidget;lastText:Ljava/util/List;'):
        assert selector in mixin, 'Missing mixin selector: ' + selector
    assert 'remap=false' in mixin
    assert 'writeClientAction' not in mixin, 'Owned clicks must stay local'
    for cls in ('ClientHighlight', 'HighlightRenderer'):
        subscriber = inspect(args.jar, 'io.github.jopika.gtmultiblocklocator.client.' + cls, verbose=True)
        assert 'net.minecraftforge.fml.common.Mod$EventBusSubscriber(' in subscriber
        assert 'CLIENT' in subscriber, 'Client subscriber missing physical-side gate'
    pinned = ROOT / 'libs/ldlib-forge-1.20.1-1.0.52.a.jar'
    panel = inspect(pinned, 'com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget')
    for signature in ('public void formatDisplayText();', 'public void updateScreen();',
                      'public boolean mouseClicked(double, double, int);',
                      'protected net.minecraft.network.chat.Style getStyleUnderMouse(double, double);'):
        assert signature in panel, 'Pinned mixin target changed: ' + signature
    # A redirect with require=1 must find exactly one GETFIELD in the target method.
    formatting = panel.split('public void formatDisplayText();', 1)[1].split('protected net.minecraft.network.chat.Style', 1)[0]
    assert len(re.findall(r'getfield\s+.*Field lastText:Ljava/util/List;', formatting)) == 1
    print(f'Production artifact verified ({len(classes)} classes): metadata, client mixins/refmap, pinned selectors,')
    print('targeted SRG mappings and dependency exclusions. Runtime mixin application/rendering untested.')


if __name__ == '__main__':
    main()
