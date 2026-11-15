#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")" && pwd)
revision=82535fe85249bcbcb5705521ce3458c78504233e
sdk="$root/.build/wp2-apps"
if [ ! -d "$sdk/.git" ]; then
    git clone https://github.com/cmf-tech-community/wp2-apps.git "$sdk"
    git -C "$sdk" checkout --detach "$revision"
fi
test "$(git -C "$sdk" rev-parse HEAD)" = "$revision"
mkdir -p "$sdk/sdk/examples/ha-for-cmf/src"
cp "$root/watch/Cargo.toml" "$sdk/sdk/examples/ha-for-cmf/Cargo.toml"
cp "$root/watch/src/main.rs" "$sdk/sdk/examples/ha-for-cmf/src/main.rs"
python3 - "$sdk/sdk/Cargo.toml" <<'PYTHON'
from pathlib import Path
import sys

path = Path(sys.argv[1])
manifest = path.read_text()
member = '"examples/ha-for-cmf"'
if member not in manifest:
    manifest = manifest.replace("members = [", f"members = [{member}, ", 1)
    path.write_text(manifest)
PYTHON
cd "$sdk"
nix-shell -p llvm --run 'scripts/build-app ha-for-cmf 1'
cp target/ha-for-cmf.wp2app "$root/.build/"
