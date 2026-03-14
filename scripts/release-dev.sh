#!/usr/bin/env bash

set -euo pipefail

readonly REMOTE="${REMOTE:-origin}"
readonly BRANCH="main"

run_tests=true
push_tag=true

usage() {
    cat <<'EOF'
Usage: scripts/release-dev.sh [--dry-run] [--skip-tests]

Creates and pushes the DEV release tag for the current VERSION after validating:
- the worktree is clean
- the current branch is main
- HEAD matches origin/main
- VERSION matches <major>.<minor>.<patch>-DEV-<n>
- VERSION is the next logical DEV version after the latest v* tag
EOF
}

fail() {
    echo "Error: $*" >&2
    exit 1
}

# Parse a DEV version and return its numeric components for comparison.
parse_dev_version() {
    local version="$1"
    local label="$2"

    if [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)-DEV-([1-9][0-9]*)$ ]]; then
        echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]} ${BASH_REMATCH[3]} ${BASH_REMATCH[4]}"
        return 0
    fi

    fail "$label '$version' must match <major>.<minor>.<patch>-DEV-<n>"
}

# Parse the base semantic version and ignore any prerelease suffix.
parse_base_version() {
    local version="$1"
    local label="$2"

    if [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-.+)?$ ]]; then
        echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]} ${BASH_REMATCH[3]}"
        return 0
    fi

    fail "$label '$version' does not start with a semantic version"
}

# Compare only the base semantic versions so new release lines can restart at DEV-1.
compare_base_versions() {
    local a_major="$1"
    local a_minor="$2"
    local a_patch="$3"
    local b_major="$4"
    local b_minor="$5"
    local b_patch="$6"

    if (( a_major != b_major )); then
        (( a_major > b_major )) && echo gt || echo lt
        return
    fi

    if (( a_minor != b_minor )); then
        (( a_minor > b_minor )) && echo gt || echo lt
        return
    fi

    if (( a_patch != b_patch )); then
        (( a_patch > b_patch )) && echo gt || echo lt
        return
    fi

    echo eq
}

# Enforce a simple monotonic DEV scheme:
# - first release in the repo must be *-DEV-1
# - same base version must increment DEV by exactly one
# - a newer base version must restart at *-DEV-1
validate_next_version() {
    local current_version="$1"
    local latest_tag="$2"

    local current_major current_minor current_patch current_dev
    read -r current_major current_minor current_patch current_dev \
        <<<"$(parse_dev_version "$current_version" "VERSION")"

    if [[ -z "$latest_tag" ]]; then
        (( current_dev == 1 )) || fail "First release in the repository must use -DEV-1"
        return
    fi

    local latest_version="${latest_tag#v}"
    local latest_major latest_minor latest_patch
    read -r latest_major latest_minor latest_patch \
        <<<"$(parse_base_version "$latest_version" "Latest tag")"

    case "$(compare_base_versions \
        "$current_major" "$current_minor" "$current_patch" \
        "$latest_major" "$latest_minor" "$latest_patch")" in
        lt)
            fail "VERSION '$current_version' is older than latest tag '$latest_tag'"
            ;;
        eq)
            if [[ ! "$latest_version" =~ -DEV-([1-9][0-9]*)$ ]]; then
                fail "VERSION '$current_version' reuses the base version of latest tag '$latest_tag'"
            fi

            local latest_dev="${BASH_REMATCH[1]}"
            local expected_dev=$(( latest_dev + 1 ))
            (( current_dev == expected_dev )) || fail \
                "VERSION '$current_version' must increment the DEV sequence after '$latest_tag' to -DEV-$expected_dev"
            ;;
        gt)
            (( current_dev == 1 )) || fail \
                "VERSION '$current_version' starts a new release line after '$latest_tag' and must use -DEV-1"
            ;;
    esac
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            push_tag=false
            ;;
        --skip-tests)
            run_tests=false
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            fail "Unknown argument '$1'"
            ;;
    esac
    shift
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

git remote get-url "$REMOTE" >/dev/null 2>&1 || fail "Git remote '$REMOTE' does not exist"

# Releasing from anything other than main makes the tag semantics ambiguous.
current_branch="$(git branch --show-current)"
[[ "$current_branch" == "$BRANCH" ]] || fail "Current branch is '$current_branch', expected '$BRANCH'"

# Refuse to tag a dirty checkout so the tag always matches a committed tree.
[[ -z "$(git status --porcelain)" ]] || fail "Worktree must be clean before creating a release tag"

git fetch "$REMOTE" "$BRANCH" --tags

local_head="$(git rev-parse HEAD)"
remote_head="$(git rev-parse "$REMOTE/$BRANCH")"

# Require the local branch to match origin/main exactly. That avoids tagging
# stale commits and also prevents creating a release from unpushed local commits.
if [[ "$local_head" != "$remote_head" ]]; then
    if [[ "$(git merge-base HEAD "$REMOTE/$BRANCH")" == "$local_head" ]]; then
        fail "Local '$BRANCH' is behind '$REMOTE/$BRANCH'"
    fi

    if [[ "$(git merge-base HEAD "$REMOTE/$BRANCH")" == "$remote_head" ]]; then
        fail "Local '$BRANCH' has unpushed commits; push or reset before releasing"
    fi

    fail "Local '$BRANCH' has diverged from '$REMOTE/$BRANCH'"
fi

version="$(tr -d '\n' < VERSION)"
tag="v$version"

# Determine whether VERSION is the next allowed DEV tag after the latest release tag.
validate_next_version "$version" "$(git tag -l 'v*' --sort=version:refname | tail -n 1)"

[[ -z "$(git tag -l "$tag")" ]] || fail "Tag '$tag' already exists"

# Run the same test entrypoint that CI uses before creating the release tag.
if $run_tests; then
    ./gradlew :test:check
fi

echo "Releasing $tag from $(git rev-parse --short HEAD)"

if $push_tag; then
    # An annotated tag is the release trigger for the DEV publish workflow.
    git tag -a "$tag" -m "DEV release $version"
    git push "$REMOTE" "$tag"
else
    echo "Dry run: skipping tag creation and push"
fi
