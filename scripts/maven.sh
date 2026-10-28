#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cached_jdk="$HOME/.cache/galactic-spacefarer-tools/sapmachine-jdk-21.0.13.0.3.jdk/Contents/Home"

# a helyi jdk, ha ezen a gépen már elő lett készítve
if [ -x "$cached_jdk/bin/java" ]; then
  export JAVA_HOME="$cached_jdk"
fi

# a cds json kimenetét a globális DEBUG elrontja
unset DEBUG
cd "$project_dir"
exec ./mvnw "$@"
