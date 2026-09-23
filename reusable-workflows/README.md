# Reusable Minecraft release workflow

`minecraft-release.yml` is a byte-for-byte copy of
[`.github/workflows/reusable-release.yml`](../.github/workflows/reusable-release.yml)
(CI fails if they drift). It is kept here so it can be moved to a dedicated repository
without digging it out of this project.

What it does, for any project with a Gradle (Java/Fabric) mod and/or an npm-built Bedrock add-on:

1. Derives the version from the tag (`v1.2.3` -> `1.2.3`) and the release type
   (`-alpha*`/`-dev*`/`-snapshot*` -> alpha, any other suffix -> beta, none -> release).
2. Builds each edition with `MOD_VERSION` exported and collects exactly one file per edition.
3. Builds a changelog: the matching `CHANGELOG.md` section, or conventional-commit groups since
   the previous tag, or GitHub-generated notes.
4. Creates/updates the GitHub Release with both files.
5. Uploads both files to CurseForge (Java project on `minecraft.curseforge.com`, Bedrock project
   on `minecraft-bedrock.curseforge.com`), resolving game version / loader names to ids.

Full input reference and a complete caller example: [`docs/ci.md`](../docs/ci.md#reusing-the-release-workflow-in-another-project).

## Using it without moving it

```yaml
jobs:
  release:
    permissions:
      contents: write
    uses: nezo32/burmaldaholic/.github/workflows/reusable-release.yml@main  # or a tag / SHA
    with:
      java-dir: .
      game-versions: "26.2,26.3"
      curseforge-java-project-id: ${{ vars.CURSEFORGE_JAVA_PROJECT_ID }}
    secrets:
      CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}
```

The repository hosting a reusable workflow must be public, or (for private repos) grant access
under *Settings -> Actions -> General -> Access*.

## Moving it to a dedicated repository

GitHub only runs reusable workflows from `.github/workflows/` of the hosting repository.
`nezo32` is a user account, so there is no organization-wide `.github` repository with
special semantics; any public repo works. Recommended: `nezo32/workflows`.

```bash
gh repo create nezo32/workflows --public --clone
cd workflows
mkdir -p .github/workflows
cp ../burmaldaholic/reusable-workflows/minecraft-release.yml .github/workflows/minecraft-release.yml
git add . && git commit -m "feat: minecraft release workflow" && git push
git tag v1 && git push origin v1          # callers pin @v1; move the tag for compatible fixes
```

Then in each project (including this one) change the caller:

```yaml
    uses: nezo32/workflows/.github/workflows/minecraft-release.yml@v1
```

Notes after moving:

- Callers must still grant `permissions: contents: write` on the calling job: a called workflow
  can only reduce, never raise, the caller's token permissions.
- `secrets: inherit` also works instead of listing `CURSEFORGE_TOKEN` explicitly.
- Remove the "Reusable workflow copy is in sync" step from this repo's `ci.yml` (or point it at
  the new location) and delete `.github/workflows/reusable-release.yml` + this directory here.
- For supply-chain safety, callers can pin a commit SHA instead of `@v1`.
