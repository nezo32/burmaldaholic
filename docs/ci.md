# CI/CD

| File | Purpose |
|------|---------|
| `.github/workflows/ci.yml` | PR / `main` checks: Java build+tests, Bedrock build+tests+lint, workflow lint, aggregator `ci-ok` |
| `.github/workflows/release.yml` | Tag `vX.Y.Z` -> calls the reusable release workflow with this project's inputs |
| `.github/workflows/reusable-release.yml` | Generic, reusable (`workflow_call`) tag -> build -> GitHub Release -> CurseForge pipeline |
| `reusable-workflows/minecraft-release.yml` | Verbatim copy of the above, ready to move to `nezo32/workflows` (see its [README](../reusable-workflows/README.md)) |
| `.github/dependabot.yml` | Weekly updates for GitHub Actions and Bedrock npm dev-dependencies |

## Branch flow

Feature-based flow with a single long-lived branch:

```
main  ──●────────●──────────●───── (tag v1.2.0) ──●── (tag v1.2.1)
         \      /  \        /
          feature/slots   fix/roulette-odds
```

1. Branch from `main`: `feature/<topic>` (also accepted: `fix/ hotfix/ chore/ docs/ ci/ refactor/ test/ release/ dependabot/ claude/` — the last for Claude Code agent branches;
   override the list with the repo variable `ALLOWED_BRANCH_PREFIXES`, space-separated).
2. Open a PR to `main`. CI runs; `ci-ok` must be green; get a review; squash-merge.
3. Use [Conventional Commits](https://www.conventionalcommits.org/) for PR titles / squash
   commits (`feat(slots): ...`, `fix: ...`, `feat!: ...`). The release changelog is grouped from them.
4. Release by tagging a commit on `main` (see [Releasing](#releasing)).

## PR checks (`ci.yml`)

| Job | Runs when | Does |
|-----|-----------|------|
| `changes` | always | `dorny/paths-filter` decides which editions changed (everything runs on `main` pushes, merge queue and manual runs) |
| `branch-name` | PRs | head branch must use an allowed prefix |
| `java (MC 26.2)`, `java (MC 26.3)` | `java/**` changed | JDK `vars.JAVA_VERSION` (default 25), `./gradlew build -Pmc=<mc>` (compile + unit tests per supported Minecraft version); uploads the jar (7 days) |
| `bedrock` | `bedrock/**` changed | Node `vars.NODE_VERSION` (default 22), `npm ci && npm run build && npm test && npm run lint`; uploads the `.mcaddon` |
| `workflows` | `.github/**` or `reusable-workflows/**` changed | actionlint (+ shellcheck of inline scripts); checks the reusable copy is in sync |
| **`ci-ok`** | always | fails if any job above failed or was cancelled; skipped jobs count as success |

Caching: Gradle via `gradle/actions/setup-gradle` (wrapper, dependencies, Loom/Minecraft
artifacts; written on `main`, read-only on PRs), npm via `actions/setup-node` `cache: npm`
keyed on `bedrock/package-lock.json`.

The Minecraft versions of the Java matrix come from the repo variable `JAVA_MC_VERSIONS`
(JSON array, default `["26.2","26.3"]`); keep it in sync with `supported_mc_range` in
`java/gradle.properties`.

### Branch protection (one-time setup)

*Settings -> Branches -> Add branch ruleset* (or classic rule) for `main`:

1. Require a pull request before merging (1 approval; dismiss stale approvals).
2. Require status checks to pass -> add **only `ci-ok`** (it appears after CI ran once).
   Do *not* add the edition jobs: they are skipped by path filters and would block the PR.
3. Require branches to be up to date before merging (or enable a merge queue; `ci.yml` handles `merge_group`).
4. Block force pushes and deletions. Optionally require linear history (squash merge).
5. *Settings -> General*: allow squash merging only; "Automatically delete head branches".
6. Optional tag protection: ruleset on tags `v*` restricting creation to maintainers.

CLI equivalent:

```bash
gh api -X POST repos/nezo32/burmaldaholic/rulesets --input - <<'EOF'
{
  "name": "main", "target": "branch", "enforcement": "active",
  "conditions": {"ref_name": {"include": ["~DEFAULT_BRANCH"], "exclude": []}},
  "rules": [
    {"type": "deletion"}, {"type": "non_fast_forward"},
    {"type": "pull_request", "parameters": {"required_approving_review_count": 1,
      "dismiss_stale_reviews_on_push": true, "require_code_owner_review": false,
      "require_last_push_approval": false, "required_review_thread_resolution": true}},
    {"type": "required_status_checks", "parameters": {"strict_required_status_checks_policy": true,
      "required_status_checks": [{"context": "ci-ok"}]}}
  ]
}
EOF
```

## Secrets and variables

*Settings -> Secrets and variables -> Actions*.

| Kind | Name | Required | Meaning |
|------|------|----------|---------|
| Secret | `CURSEFORGE_TOKEN` | for CurseForge | Upload API token from <https://legacy.curseforge.com/account/api-tokens> (one token works for both the Java and Bedrock hosts). Missing -> publishing is skipped with a warning; the GitHub Release is still created. |
| Variable | `CURSEFORGE_JAVA_PROJECT_ID` | for CurseForge | Numeric project id of the Java mod (project page -> "About Project" -> Project ID). Empty -> Java upload skipped. |
| Variable | `CURSEFORGE_BEDROCK_PROJECT_ID` | for CurseForge | Numeric project id of the Bedrock add-on. Empty -> Bedrock upload skipped. |
| Variable | `CURSEFORGE_GAME_VERSIONS` | no | Default `26.2,26.3`. Names as shown on CurseForge; `26.2*` matches all `26.2.x`. |
| Variable | `CURSEFORGE_BEDROCK_GAME_VERSIONS` | no | Bedrock versions if they are named differently from Java ones (default: same as above). |
| Variable | `CURSEFORGE_JAVA_VERSIONS` | no | e.g. `Java 25` (Java version tags on the file). |
| Variable | `CURSEFORGE_JAVA_RELATIONS` | no | Default `fabric-api:requiredDependency`. Comma list of `slug:type`. |
| Variable | `JAVA_VERSION` | no | JDK for CI and releases (default `25`, required by Minecraft 26.x / Loom 1.18). |
| Variable | `NODE_VERSION` | no | Node.js for CI and releases (default `22`). |
| Variable | `JAVA_MC_VERSIONS` | no | CI matrix, JSON array (default `["26.2","26.3"]`). |
| Variable | `ALLOWED_BRANCH_PREFIXES` | no | Branch-name check, space-separated. |

The `GITHUB_TOKEN` is used for the release; `release.yml` grants it `contents: write`, and only
the reusable workflow's `release` job uses that permission (every other job is `contents: read`).

## Build contract (for the Java and Bedrock builds)

The release pipeline passes the version derived from the tag to the builds. **Both builds must honor it.**

- `MOD_VERSION` environment variable: SemVer from the tag without `v`, e.g. `1.2.3`,
  `1.3.0-beta.1`. Not set in PR CI -> the build uses its own development default
  (`mod_version` in `java/gradle.properties`, `version` in `bedrock/package.json`).

**Java (`java/`)**, run as `./gradlew build --no-daemon --stacktrace -Pmod_version="$MOD_VERSION"`:
- `-Pmod_version` overrides `gradle.properties`; `version = project.mod_version` must flow into
  the jar name and `fabric.mod.json` (`"version": "${version}"`). Reading
  `System.getenv("MOD_VERSION")` as a fallback is welcome but not required.
- `build` must run the unit tests. Must accept `-Pmc=<version>` (used by the CI matrix; release
  builds use the default = lowest supported Minecraft version, one jar for the whole range).
- Output: exactly one release jar in `java/build/libs/` besides `*-sources.jar`, `*-dev.jar`,
  `*-javadoc.jar` (e.g. `burmaldaholic-1.2.3.jar`).

**Bedrock (`bedrock/`)**, run as `npm ci && npm run build && npm test`:
- `npm run build` reads `process.env.MOD_VERSION` and writes it into every `manifest.json`
  (`header.version` and the matching `modules[].version` / `dependencies[].version` between our
  own packs). Manifest `format_version` 3 takes a semver string (`"1.2.3"`, pre-release suffix
  allowed); format 2 takes `[major, minor, patch]`. This project uses format 3.
- Scripts `build`, `test` and `lint` must exist (CI runs all three); `package-lock.json` must be committed.
- Output: exactly one `bedrock/dist/*.mcaddon` (e.g. `Burmaldaholic.mcaddon`; the release renames
  it to `Burmaldaholic-1.2.3.mcaddon` automatically).

## Releasing

1. Make sure `main` is green. Optionally add a `## [1.2.3] - YYYY-MM-DD` section to `CHANGELOG.md`
   (Keep a Changelog style; `## 1.2.3` and `## v1.2.3` also work). If there is no such section,
   the changelog is generated from conventional commits since the previous tag.
2. Tag and push:
   ```bash
   git checkout main && git pull
   git tag -a v1.2.3 -m "v1.2.3"
   git push origin v1.2.3
   ```
   Pre-releases: `v1.3.0-beta.1` / `v1.3.0-rc.1` -> CurseForge *beta* + GitHub pre-release;
   `v1.3.0-alpha.1` -> *alpha*.
3. The **Release** workflow: resolves version & changelog -> builds the jar and the `.mcaddon` in
   parallel (with tests) -> creates the GitHub Release with both files -> uploads to CurseForge
   (Java file tagged with game versions + `Fabric` + `Client`/`Server` environments, Bedrock file
   with its game versions). CurseForge files then go through CurseForge moderation.
4. Re-running: re-run failed jobs from the Actions UI, or *Actions -> Release -> Run workflow* with
   the tag and `dry-run: false`. An existing GitHub Release is updated (assets replaced), not duplicated.
   Note that CurseForge has no "replace": re-running a successful CurseForge upload creates a second file.

### Dry run

*Actions -> Release -> Run workflow*, enter a tag (need not exist, e.g. `v0.0.1-alpha.1`), keep
`dry-run` checked. Everything is built and the changelog computed; the GitHub Release is not created,
and the CurseForge step prints the exact metadata JSON it would POST (with resolved game-version ids
when `CURSEFORGE_TOKEN` is set, with plain names otherwise). Use it to verify version names such as
`26.2` exist on CurseForge before the first real release.

## CurseForge upload details

Implemented with `curl` + `jq` inside the reusable workflow (no third-party action):

| | Java edition | Bedrock edition |
|-|-|-|
| API host | `https://minecraft.curseforge.com` | `https://minecraft-bedrock.curseforge.com` |
| Version list | `GET /api/game/version-types`, `GET /api/game/versions` | same |
| Upload | `POST /api/projects/{id}/upload-file` (multipart `metadata` JSON + `file`) | same |
| Auth | `X-Api-Token: $CURSEFORGE_TOKEN` | same |

Metadata sent: `changelog` (markdown), `changelogType: "markdown"`, `releaseType`,
`gameVersions` (ids), optional `displayName` and `relations.projects[{slug,type}]`.

Name -> id resolution: Java game versions are looked up in version types whose slug starts with
`minecraft`, loaders in `modloader`, Java tags in `java`, environments in `environment`; Bedrock
versions in all types (narrow with input `curseforge-bedrock-version-type-prefix`). Names match
the version `name` or `slug` case-insensitively; a trailing `*` matches by prefix. An unknown name
fails the job (`curseforge-strict-versions: true`) so a typo never publishes an untagged file.

Why not `Kir-Antipov/mc-publish` (latest v3.3.1): it supports CurseForge only for the Java host
(its upload client hardcodes `minecraft.curseforge.com`), so the Bedrock add-on would need a second
mechanism anyway, and its Minecraft-version normalization (Mojang manifest + hand-written 1.x
snapshot mappings) is unproven with the year-based `26.x` names. The direct API keeps one code path
for both editions. Assumptions to verify with a dry run before the first release: the exact
CurseForge names of the 26.x game versions on each host, and that the Bedrock project accepts
`.mcaddon` uploads (it accepts `.mcaddon`/`.mcpack`/`.mcworld`/`.zip`).

## Reusing the release workflow in another project

The pipeline is generic: every edition is optional, all paths/commands/globs are inputs. A
complete caller (e.g. a Fabric-only mod repo with the Gradle project at the root):

```yaml
# .github/workflows/release.yml in another repository
name: Release
on:
  push:
    tags: ["v*.*.*"]
  workflow_dispatch:
    inputs:
      tag: { description: "Tag, e.g. v1.0.0", required: true, type: string }
      dry-run: { type: boolean, default: true }

concurrency:
  group: release-${{ inputs.tag || github.ref }}
  cancel-in-progress: false

permissions:
  contents: read

jobs:
  release:
    permissions:
      contents: write
    uses: nezo32/burmaldaholic/.github/workflows/reusable-release.yml@main   # pin a tag/SHA in real use
    with:
      tag: ${{ inputs.tag || github.ref_name }}
      dry-run: ${{ inputs.dry-run || false }}
      release-name: "My Mod {version}"

      # Java edition (omit java-dir to skip)
      java-dir: "."
      java-version: "25"
      java-build-command: ./gradlew build --no-daemon -Pmod_version="$MOD_VERSION"
      java-artifact-glob: build/libs/*.jar
      java-artifact-exclude: "*-sources.jar *-dev.jar"

      # Bedrock edition (omit bedrock-dir to skip)
      # bedrock-dir: bedrock
      # node-version: "22"
      # bedrock-build-command: npm ci && npm run build
      # bedrock-artifact-glob: dist/*.mcaddon

      changelog-mode: auto              # auto | file | commits | github | none
      changelog-file: CHANGELOG.md
      append-github-notes: true

      curseforge-java-project-id: "123456"
      # curseforge-bedrock-project-id: "654321"
      game-versions: "26.2,26.3"
      loaders: "fabric,quilt"
      curseforge-java-versions: "Java 25"
      curseforge-environments: "client,server"
      curseforge-java-relations: "fabric-api:requiredDependency,modmenu:optionalDependency"
      curseforge-display-name: "My Mod {version}"
    secrets:
      CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}
```

### Inputs

| Input | Default | Notes |
|-------|---------|-------|
| `tag` | `github.ref_name` | tag being released |
| `tag-prefix` | `v` | stripped to get the version; also used to find the previous tag |
| `version` | from tag | explicit SemVer override |
| `release-name` | `{repo} {version}` | placeholders `{version} {tag} {repo}` |
| `release-type` | `auto` | `auto`, `release`, `beta`, `alpha` |
| `create-github-release` / `draft` | `true` / `false` | |
| `ensure-version-in-filename` | `true` | `X.mcaddon` -> `X-1.2.3.mcaddon` |
| `dry-run` | `false` | no release, no upload; prints requests |
| `runs-on` | `ubuntu-latest` | |
| `artifact-retention-days` | `14` | intermediate artifacts |
| `changelog-mode` | `auto` | `auto` = CHANGELOG section, else conventional commits since previous tag |
| `changelog-file` | `CHANGELOG.md` | |
| `append-github-notes` | `false` | append GitHub's generated notes (PRs, new contributors) |
| `java-dir` | `""` (skip) | Gradle project dir |
| `java-version` / `java-distribution` | `25` / `temurin` | |
| `java-build-command` | `./gradlew build --no-daemon --stacktrace -Pmod_version="$MOD_VERSION"` | runs in `java-dir` |
| `java-artifact-glob` / `java-artifact-exclude` | `build/libs/*.jar` / `*-sources.jar *-dev.jar *-javadoc.jar *-dev-shadow.jar` | must leave exactly one file |
| `bedrock-dir` | `""` (skip) | npm project dir |
| `node-version` / `node-cache` | `22` / `npm` | cache keyed on `<bedrock-dir>/package-lock.json` |
| `bedrock-build-command` | `npm ci && npm run build && npm test` | runs in `bedrock-dir` |
| `bedrock-artifact-glob` / `bedrock-artifact-exclude` | `dist/*.mcaddon` / `""` | must leave exactly one file |
| `curseforge-java-project-id` / `curseforge-bedrock-project-id` | `""` | empty = skip that upload |
| `game-versions` | `""` | comma list, `*` suffix = prefix match |
| `curseforge-bedrock-game-versions` | = `game-versions` | |
| `loaders` | `fabric` | Java file only |
| `curseforge-java-versions` / `curseforge-environments` | `""` | Java file only |
| `curseforge-java-relations` / `curseforge-bedrock-relations` | `""` | `slug:type,...` |
| `curseforge-display-name` | file name | placeholders `{version} {tag} {repo} {edition}` |
| `curseforge-strict-versions` | `true` | fail on unknown names |
| `curseforge-java-api-url` / `curseforge-bedrock-api-url` | see table above | |
| `curseforge-bedrock-version-type-prefix` | `""` | |

Secret: `CURSEFORGE_TOKEN` (optional). Outputs: `version`, `release-type`, `release-url`.

Requirements for the calling repo: the calling job grants `permissions: contents: write`; tags
are SemVer (`v1.2.3`, `v1.2.3-beta.1`); each build honors `MOD_VERSION` as described in
[Build contract](#build-contract-for-the-java-and-bedrock-builds). Don't give the calling
workflow a concurrency group named like any group inside the called workflow (there is none today).

## Maintenance

- Actions are pinned to major versions (`actions/checkout@v7`, `setup-java@v6`, `setup-node@v7`,
  `upload-artifact@v7`, `download-artifact@v8`, `gradle/actions/setup-gradle@v6`,
  `dorny/paths-filter@v4`); Dependabot bumps them. Pin SHAs if you need stricter supply-chain control.
- Lint locally: `actionlint .github/workflows/*.yml reusable-workflows/*.yml` (with `shellcheck` on `PATH`).
- Edit `.github/workflows/reusable-release.yml`, then `cp` it to `reusable-workflows/minecraft-release.yml`
  (CI checks they are identical).
