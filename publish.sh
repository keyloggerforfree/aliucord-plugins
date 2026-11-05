#!/usr/bin/env bash
# Builds all plugins and publishes the zips + updater.json to the `builds` branch,
# which the Aliucord updater reads (see updateUrl/buildUrl in build.gradle.kts).
#
# Usage: ./publish.sh [--dry-run] [--allow-dirty] [--no-build]
#   --dry-run      build and create the commit, but don't push it
#   --allow-dirty  publish even if the working tree has uncommitted changes
#   --no-build     skip the Gradle build (used by `./gradlew publishBuilds`, which builds first)
#
# Env: REMOTE (default: origin), BRANCH (default: builds), JAVA_HOME (default: JDK 17 if installed)
set -euo pipefail

cd "$(dirname "$0")"

remote="${REMOTE:-origin}"
branch="${BRANCH:-builds}"
dry_run=0
allow_dirty=0
build=1
for arg in "$@"; do
    case "$arg" in
        --dry-run) dry_run=1 ;;
        --allow-dirty) allow_dirty=1 ;;
        --no-build) build=0 ;;
        -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown argument: $arg" >&2; exit 1 ;;
    esac
done

if [[ $allow_dirty -eq 0 && -n "$(git status --porcelain)" ]]; then
    echo "Working tree has uncommitted changes; commit them first or pass --allow-dirty." >&2
    exit 1
fi

if [[ $build -eq 1 ]]; then
    # Gradle 7.6 doesn't run on JDK 20+
    if [[ -z "${JAVA_HOME:-}" && -d /usr/lib/jvm/java-17-openjdk ]]; then
        export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
    fi
    # clean: make doesn't notice version/author/description changes, so the zip's manifest could be stale
    ./gradlew clean make generateUpdaterJson --console=plain
fi

shopt -s nullglob
zips=(*/build/*.zip)
if [[ ${#zips[@]} -eq 0 || ! -f build/updater.json ]]; then
    echo "Build produced no zips or no build/updater.json" >&2
    exit 1
fi

# Build the commit in a temporary index so the checkout and the real index are untouched
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
export GIT_INDEX_FILE="$tmp/index"

parent=""
if git fetch --quiet "$remote" "refs/heads/$branch" 2>/dev/null; then
    parent="$(git rev-parse FETCH_HEAD)"
    git read-tree "$parent"   # keep files already published
else
    echo "No $branch branch on $remote yet, creating it"
    git read-tree --empty
fi

for f in "${zips[@]}" build/updater.json; do
    blob="$(git hash-object -w "$f")"
    git update-index --add --cacheinfo "100644,$blob,$(basename "$f")"
    echo "  $(basename "$f")"
done

tree="$(git write-tree)"
if [[ -n "$parent" && "$tree" == "$(git rev-parse "$parent^{tree}")" ]]; then
    echo "Nothing changed since the last publish."
    exit 0
fi

source_rev="$(git rev-parse --short HEAD 2>/dev/null || echo "no commit")"
[[ -n "$(git status --porcelain)" ]] && source_rev="$source_rev + uncommitted changes"
commit="$(git commit-tree "$tree" ${parent:+-p "$parent"} -m "Build from $source_rev")"

if [[ $dry_run -eq 1 ]]; then
    echo "Dry run: created $commit, not pushing. Contents:"
    git ls-tree --name-only "$commit"
    exit 0
fi

git push "$remote" "$commit:refs/heads/$branch"
echo "Published $commit to $remote/$branch"
