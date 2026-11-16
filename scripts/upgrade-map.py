#!/usr/bin/env python3
"""Reproduce the game map by upgrading the user's immutable HTML upload."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'integration/map-upgrade/upstream/chronicle_world_map_v3_2.html'
OUTPUT=ROOT/'app/src/main/assets/maps/chronicle_world_map.html'
PATCH=ROOT/'integration/map-upgrade/upgrade.patch'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--write',action='store_true',help='Regenerate the Android asset')
parser.add_argument('--check',action='store_true',help='Verify the checked-in asset (default)')
args=parser.parse_args()
raw=SOURCE.read_bytes()
assert hashlib.sha256(raw).hexdigest()=='2553b2c5af23f80895a315154ece758d728813c39095f9649d9aad230a82fd56','User source changed'
with tempfile.TemporaryDirectory(prefix='chronicle-map-') as temp:
 target=Path(temp)/'chronicle_world_map.html'
 target.write_bytes(raw)
 result=subprocess.run(['patch','--batch','--fuzz=0',str(target),str(PATCH)],check=True,capture_output=True,text=True)
 upgraded=target.read_bytes()
 assert b'chronicle-map-scenario/v2' in upgraded and b'applyWorldDelta' in upgraded
 if args.write:OUTPUT.write_bytes(upgraded)
 assert OUTPUT.read_bytes()==upgraded,'Android map differs from the upgraded user upload'
 assert not (OUTPUT.parent/'chronicle_world_map_v4.html').exists(),'Obsolete Android map must not remain'
 print('Verified user HTML -> upgrade.patch -> active Android map; SHA-256 '+hashlib.sha256(upgraded).hexdigest())
