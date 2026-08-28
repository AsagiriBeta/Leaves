#!/usr/bin/env bash

prop() {
  grep "^[[:space:]]*${1}[[:space:]]*=" gradle.properties | grep -v "^[[:space:]]*#" | cut -d'=' -f2 | sed 's/\r//'
}

latest_build=$(curl -fsS -L "https://api.leavesmc.org/v2/projects/leaves/versions/$(prop mcVersion)/latestGroupBuildId" || true)

if [[ $latest_build =~ ^[0-9]+$ ]]; then
    echo "BUILD_NUMBER=$((latest_build + 1))" >> "$GITHUB_ENV"
elif [[ "${GITHUB_RUN_NUMBER:-}" =~ ^[0-9]+$ ]]; then
    echo "Official Leaves API has no build id for $(prop mcVersion); using GitHub run number ${GITHUB_RUN_NUMBER}"
    echo "BUILD_NUMBER=${GITHUB_RUN_NUMBER}" >> "$GITHUB_ENV"
else
    echo "Error: Received non-integer value from API: ${latest_build:-<empty>}"
    exit 1
fi
