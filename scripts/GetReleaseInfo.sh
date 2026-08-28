#!/usr/bin/env bash

sha256() {
  sha256sum "$1" | awk '{print $1}'
}

sha1() {
  sha1sum "$1" | awk '{print $1}'
}

md5() {
  md5sum "$1" | awk '{print $1}'
}

prop() {
  grep "^[[:space:]]*${1}[[:space:]]*=" gradle.properties | grep -v "^[[:space:]]*#" | cut -d'=' -f2 | sed 's/\r//'
}

commitid=$(git log --pretty='%h' -1)
mcversion=$(prop mcVersion)
gradleVersion=$(prop version)
preVersion=$(prop preVersion)
repo="${GITHUB_REPOSITORY:-$(prop githubRepo)}"
repo="${repo:-LeavesMC/Leaves}"
tagid="$mcversion-$BUILD_NUMBER-$commitid"
jarName="leaves-$mcversion.jar"
leavesid="Leaves-$tagid"
releaseinfo="releaseinfo.md"
discordmes="discordmes.json"
make_latest=$([ "$preVersion" = "true" ] && echo "false" || echo "true")

rm -f $discordmes
rm -f $releaseinfo

mv leaves-server/build/libs/leaves-leavesclip-"$gradleVersion".jar "$jarName"
{
  echo "name=$leavesid"
  echo "tag=$tagid"
  echo "jar=$jarName"
  echo "info=$releaseinfo"
  echo "discordmes=$discordmes"
  echo "pre=$preVersion"
  echo "make_latest=$make_latest"
} >> "$GITHUB_ENV"

{
  echo "$leavesid [![download](https://img.shields.io/github/downloads/$repo/$tagid/total?color=0)](https://github.com/$repo/releases/download/$tagid/$jarName)"
  echo "====="
  echo ""
  if [ "$preVersion" = "true" ]; then
    echo "> This is an early, experimental build. It is only recommended for usage on test servers and should be used with caution."
    echo "> **Backups are mandatory!**"
    echo ""
  fi
  echo "### Commit Message"
} >> $releaseinfo

default_branch=$(git symbolic-ref --short refs/remotes/origin/HEAD 2>/dev/null | sed 's@^origin/@@' || true)
default_branch="${default_branch:-master}"
if git describe --tags --abbrev=0 >/dev/null 2>&1; then
  number=$(git log --oneline "$default_branch" ^"$(git describe --tags --abbrev=0)" | wc -l)
else
  number=$(git log --oneline -20 | wc -l)
fi
git log --pretty='> [%h] %s' "-$number" >> $releaseinfo

{
  echo ""
  echo "### Checksum"
  echo "| File | $jarName |"
  echo "| ---- | ---- |"
  echo "| MD5 | $(md5 "$jarName") |"
  echo "| SHA1 | $(sha1 "$jarName") |"
  echo "| SHA256 | $(sha256 "$jarName") |"
} >> $releaseinfo

{
  echo -n "{\"content\":\"Leaves New Release\",\"embeds\":[{\"title\":\"$leavesid\",\"url\":\"https://github.com/$repo/releases/tag/$tagid\",\"fields\":[{\"name\":\"Changelog\",\"value\":\""
  # shellcheck disable=SC2046
  echo -n $(git log --oneline --pretty='> [%h] %s\\n' "-$number")
  echo "\",\"inline\":true}]}]}"
} >> $discordmes
