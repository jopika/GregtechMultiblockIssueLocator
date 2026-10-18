#!/usr/bin/env python3
"""Prepare fingerprinted local compile inputs, or check them without copying."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mods', type=Path, nargs='?')
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--download', action='store_true', help='Download pinned inputs for CI')
    args = parser.parse_args()
    if sum((args.check, args.download, args.mods is not None)) != 1:
        parser.error('Choose a reference mods folder, --check, or --download')
    artifacts = json.loads((ROOT / 'scripts/dependencies.json').read_text())['artifacts']
    if args.download:
        # Validate both downloads before replacing any local inputs.
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)
            for artifact in artifacts:
                request = urllib.request.Request(artifact['url'], headers={'User-Agent': 'GregTechMultiblockLocator-build'})
                with urllib.request.urlopen(request, timeout=120) as response:
                    data = response.read()
                if hashlib.sha256(data).hexdigest() != artifact['sha256']:
                    raise SystemExit(f'Download hash mismatch: {artifact["file"]}')
                (source / artifact['file']).write_bytes(data)
            (ROOT / 'libs').mkdir(exist_ok=True)
            for artifact in artifacts:
                shutil.copy2(source / artifact['file'], ROOT / 'libs' / artifact['file'])
        print(f'Downloaded and verified {len(artifacts)} pinned inputs.')
        return
    source = ROOT / 'libs' if args.check else args.mods
    for artifact in artifacts:
        path = source / artifact['file']
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != artifact['sha256']:
            raise SystemExit(f'Missing or different pinned input: {path}')
    if not args.check:
        (ROOT / 'libs').mkdir(exist_ok=True)
        for artifact in artifacts:
            shutil.copy2(source / artifact['file'], ROOT / 'libs' / artifact['file'])
    print(f'{"Verified" if args.check else "Prepared"} {len(artifacts)} pinned inputs; reference instance unchanged.')

if __name__ == '__main__':
    main()
