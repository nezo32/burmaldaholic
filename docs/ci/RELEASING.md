# Releasing Burmaldaholic

Maintainer runbook. How the pipeline works in general: [REUSABLE_RELEASE_PIPELINE.md](REUSABLE_RELEASE_PIPELINE.md).
Branch and PR rules: [CONTRIBUTING.md](../../CONTRIBUTING.md).

| File | Purpose |
|---|---|
| `.github/workflows/ci.yml` | PR / `main` checks, aggregated into the single required check `ci-ok` |
| `.github/workflows/release.yml` | Tag `vX.Y.Z` → build the mod → GitHub release → CurseForge + Modrinth |
| `.github/workflows/publish-modrinth.yml` | Manual: publish the jar of an **existing** GitHub release to Modrinth only (no rebuild) |
| `.github/workflows/reusable-*.yml` | Generic reusable workflows, identical to the ones in `nezo32/enchantaholic` (`reusable-build-node.yml` is kept in sync but no job here calls it; `reusable-publish-modrinth.yml` is new here and not yet in Enchantaholic) |
| `.github/workflows/labeler.yml`, `.github/labeler.yml` | PR labels from the branch prefix and changed paths |
| `.github/release.yml` | Release-notes categories (by label) |
| `.github/templates/release-caller.yml` | Caller template for other repositories (not run here, only linted) |
| `scripts/curseforge-upload.sh`, `scripts/test/` | CurseForge upload script (inlined into `reusable-publish-curseforge.yml`) and its dry-run tests |
| `.github/dependabot.yml` | Weekly updates: GitHub Actions, Gradle (`java/`), npm (`tools/`, the asset generator) |

A release is a tag. Pushing `vX.Y.Z` on a commit of `main` starts `.github/workflows/release.yml`, which in one run:

1. `version`: parses the tag and checks that it is on `main`.
2. `build-mod`: `./gradlew build -Pmod_version=X.Y.Z` in `java/` (JDK 25; unit tests and gametests included;
   `checkLinkage` is a no-op while only one Minecraft version is supported).
3. `github-release`: the release "Burmaldaholic X.Y.Z", with generated notes and the asset `burmaldaholic-X.Y.Z.jar`.
4. `curseforge-mod` and `modrinth-mod` (in parallel): upload the same jar, using the release notes as the changelog.
   Modrinth gets version `X.Y.Z` named "Burmaldaholic X.Y.Z", game version `26.3`, loader `fabric`, a
   required dependency on Fabric API, not featured.

The mod supports **Minecraft 26.3 only** (26.2 was dropped for v0.2.0). The jar is compiled against `mc=26.3` in
`java/gradle.properties` and declares `supported_mc_range` (`~26.3`, i.e. 26.3 and its 26.3.x patches); PR CI builds
and tests it with `-Pmc=26.3`.

Never edit `mod_version` in `java/gradle.properties` to release. It is a development default; the tag sets the real
version (`-Pmod_version`).

## Cutting a release

```bash
git switch main && git pull --ff-only
git log --oneline -5                         # the commit you are about to release
git tag -a v1.2.0 -m v1.2.0
git push origin v1.2.0
```

Then watch Actions → Release. When it is green, check the GitHub release, the CurseForge file pages (CurseForge
files then go through CurseForge moderation) and the Modrinth versions page.

Before tagging, look at the merged PRs since the last release: their titles and labels are the release notes. Fix a
title or label on the PR now if needed; the notes are generated when the release job runs.

## Pre-releases

Use a SemVer pre-release suffix:

| Tag | GitHub | CurseForge / Modrinth |
|---|---|---|
| `v1.3.0-alpha.1` | pre-release | alpha |
| `v1.3.0-beta.1` | pre-release | beta |
| `v1.3.0-rc.1` | pre-release | beta |
| `v1.3.0` | latest release | release |

Pre-releases are never marked "latest" on GitHub.

## Hotfix

1. Branch from `main`: `git switch -c hotfix/<short-name> origin/main`.
2. Fix, open a PR to `main`, and squash-merge it once `ci-ok` passes.
3. Tag the merge commit with the next patch version: `git tag -a v1.2.1 -m v1.2.1 && git push origin v1.2.1`.

There are no long-lived release branches. A tag on anything other than a commit of `main` fails in the `version` job.

## Re-running and dry runs

- **Rebuild an existing tag and update its GitHub release** (for example after a failed build): Actions → Release →
  Run workflow, `tag: v1.2.0`, tick `skip-curseforge` and `skip-modrinth`. This rebuilds the jar (not
  byte-identical to the old one), replaces the release asset and regenerates the release notes (manual edits to the
  release body are lost). Never do this for a tag that is already on CurseForge or Modrinth.
- **Publish an existing release to Modrinth only** (for example a release made before Modrinth was set up): Actions →
  **Publish to Modrinth** → Run workflow, `tag: v1.2.0`. It downloads `burmaldaholic-1.2.0.jar` from the GitHub
  release (checking its sha256 against the asset digest), so Modrinth gets exactly the jar that is on GitHub and
  CurseForge; it does not touch the tag, the GitHub release or CurseForge. `dry-run` is ticked by default: run it
  once like that, then again with `dry-run` unticked.

  ```bash
  gh workflow run publish-modrinth.yml --ref main -f tag=v1.2.0 -f dry-run=true
  gh workflow run publish-modrinth.yml --ref main -f tag=v1.2.0 -f dry-run=false
  ```
- **Only a CurseForge or Modrinth job failed:** fix the cause (usually a game version name in the repo variable), then open the
  failed run and use "Re-run failed jobs". The build artifacts are reused.
- **Dry run of the CurseForge upload:** Run workflow with `tag: <existing tag>` and `curseforge-dry-run: true`.
  The CurseForge jobs resolve the version names and print the metadata without uploading. The GitHub release for that
  tag is still rebuilt and updated. `modrinth-dry-run` does the same for Modrinth (or use Publish to Modrinth with
  `dry-run`, which does not rebuild anything).
- **Modrinth is idempotent per version:** if version `X.Y.Z` already exists on the Modrinth project, the job logs a
  notice and uploads nothing. A Modrinth upload is not retried after a curl error or HTTP 5xx: look at the
  project's Versions page before re-running.
- **CurseForge has no idempotency:** re-running a successful upload creates a second file. Delete the duplicate in the
  CurseForge UI. If an upload failed with HTTP 5xx or a curl error, look at the project's Files page before
  re-running: the file may have been created anyway (the script deliberately does not retry those).
- **Wrong tag pushed:** if the run already failed or has not published yet, delete the tag
  (`git push --delete origin vX.Y.Z && git tag -d vX.Y.Z`) and any draft/created GitHub release. If it already
  reached CurseForge or Modrinth, do not reuse the version: release the next patch.

## PR checks (`ci.yml`)

| Job | Runs when | Does |
|---|---|---|
| `changes` | always | `dorny/paths-filter`: on PRs, decides which of `mod` / `assets` must run. Everything runs on `main`, merge queue and manual runs |
| `branch-name` | same-repo PRs | head branch must be `<type>/<name>` (or `dependabot/`, `renovate/`, `claude/`) |
| `actionlint` | always | actionlint 1.7.12 (+ shellcheck of `run:` scripts) on the workflows and the caller template |
| `scripts` | always | shellcheck, `scripts/test/curseforge-upload.test.sh`, `scripts/check-inlined-script.sh` |
| `mod (26.3) / build` | `java/**` or `docs/design/CONFIG.md` changed | `./gradlew build -Pmc=<mc>` via `reusable-build-gradle.yml`; uploads the jar (7 days) |
| `assets` | `tools/**`, Java textures or `docs/design/SLOTS.md` changed | `cd tools && npm ci && npm run check:assets && npm test`: the committed generated textures are byte-identical to the generator's output, and its tests pass |
| **`ci-ok`** | always | fails if any job above failed or was cancelled; skipped jobs count as success |

The Minecraft versions of the Java matrix come from the repo variable `JAVA_MC_VERSIONS` (JSON array, default
`["26.3"]`); keep it in sync with `supported_mc_versions` in `java/gradle.properties`.

## One-time repository setup

### Merge settings

Settings → General → Pull Requests: allow **squash merging** only, use the PR title as the default commit message, and
enable "Automatically delete head branches".

### Branch protection for `main`

Settings → Rules → Rulesets (or Branches) for `main`: require a pull request, require the status check **`ci-ok`
only**, require linear history, block force pushes and deletion. Do not add the path-filtered jobs (`mod (…) / build`,
`assets`): the path filters skip them on PRs that do not touch their paths, and a skipped required check blocks the
merge. `ci-ok` always reports and fails when any of them fails.

The same with the `gh` CLI:

```bash
gh api -X POST repos/nezo32/burmaldaholic/rulesets --input - <<'JSON'
{
  "name": "main", "target": "branch", "enforcement": "active",
  "conditions": {"ref_name": {"include": ["~DEFAULT_BRANCH"], "exclude": []}},
  "rules": [
    {"type": "deletion"}, {"type": "non_fast_forward"}, {"type": "required_linear_history"},
    {"type": "pull_request", "parameters": {"required_approving_review_count": 0,
      "dismiss_stale_reviews_on_push": true, "require_code_owner_review": false,
      "require_last_push_approval": false, "required_review_thread_resolution": false}},
    {"type": "required_status_checks", "parameters": {"strict_required_status_checks_policy": true,
      "required_status_checks": [{"context": "ci-ok"}]}}
  ]
}
JSON
```

A check only appears in the picker after it has run once, so open the first PR before configuring this in the UI.
`ci.yml` also handles `merge_group`, so a merge queue can be enabled instead of "require branches to be up to date".
Optionally add a tag ruleset on `v*` that restricts tag creation to maintainers.

### Labels

The labeler sets labels from the branch prefix and changed paths, and the release notes are grouped by them. Create them
once:

```bash
gh label create enhancement    --color a2eeef --description "New feature"            --force
gh label create bug            --color d73a4a --description "Bug fix"                --force
gh label create chore          --color ededed --description "Maintenance, CI, build" --force
gh label create documentation  --color 0075ca --description "Docs only"              --force
gh label create dependencies   --color 0366d6 --description "Dependency updates"     --force
gh label create breaking       --color b60205 --description "Breaking change"        --force
gh label create skip-changelog --color cccccc --description "Leave out of release notes" --force
gh label create java           --color dbab79 --description "Java / Fabric mod"      --force
gh label create tools          --color 5319e7 --description "Asset generator (tools/)" --force
```

### Releasing from the Actions tab (no local git needed)

Actions → **Release** → Run workflow, branch **main**, tag `vX.Y.Z`. If the tag does not exist yet, the workflow creates it on the tip of `main` and continues with build → GitHub release → CurseForge in the same run. If it already exists, the run rebuilds and re-publishes that release (to publish an existing release to Modrinth only, use Publish to Modrinth instead).

### Secrets and variables

Settings → Secrets and variables → Actions. The CurseForge token and project ids may be repository-level **or** live in the GitHub Environment `Burmaldaholic` (the upload jobs run in it; override the name with the repository variable `CURSEFORGE_ENVIRONMENT`). A missing project id shows up as a warning in the run, not as a silently skipped job.
The Modrinth job uses the same Environment (override with `MODRINTH_ENVIRONMENT`); a missing `MODRINTH_TOKEN` is a
warning and the Modrinth upload is skipped.

| Kind | Name | Value |
|---|---|---|
| secret | `CURSEFORGE_TOKEN` | CurseForge Authors portal → API tokens |
| variable | `CURSEFORGE_PROJECT_ID` | numeric id of the Java project (sidebar of the project page). Empty = CurseForge skipped |
| variable (optional) | `CURSEFORGE_GAME_VERSIONS` | default `26.3,Fabric,Java 25,Client,Server` |
| secret | `MODRINTH_TOKEN` | Modrinth → Settings → Personal access tokens, scopes **Create versions**, **Read projects**, **Read versions** (read is needed while the project is a draft / under review). Lives in the Environment `burmaldaholic` |
| variable (optional) | `MODRINTH_PROJECT_ID` | Modrinth project id or slug, default `burmaldaholic` |
| variable (optional) | `MODRINTH_GAME_VERSIONS` | default `26.3` (Modrinth game version tags) |
| variable (optional) | `MODRINTH_ENVIRONMENT` | Environment of the Modrinth job, default = `CURSEFORGE_ENVIRONMENT` or `Burmaldaholic` |
| variable (optional) | `JAVA_MC_VERSIONS` | PR CI matrix, JSON array, default `["26.3"]` |

```bash
gh secret set CURSEFORGE_TOKEN
gh variable set CURSEFORGE_PROJECT_ID --body 123456
```

The names are the same as in `nezo32/enchantaholic`. When a new patch of a supported Minecraft version ships (for
example `26.3.1`), add it to `CURSEFORGE_GAME_VERSIONS` and `MODRINTH_GAME_VERSIONS`.

### Confirm the CurseForge names

The defaults were verified against the live API for Enchantaholic (September 2026): on the Java host
`https://minecraft.curseforge.com`, `26.2`, `26.3`, `Fabric`, `Java 25`, `Client` and `Server` all resolve.

Check again whenever you change the variables:

```bash
T=<token>
curl -fsS -H "X-Api-Token: $T" https://minecraft.curseforge.com/api/game/versions \
  | jq -r '.[] | select(.name | test("^(26\\.|Fabric|Java 25|Client|Server)")) | "\(.id)\t\(.gameVersionTypeID)\t\(.name)"'

# the full check, without uploading:
CF_TOKEN=$T CF_PROJECT_ID=<id> CF_DRY_RUN=true \
CF_GAME_VERSIONS='26.3,Fabric,Java 25,Client,Server' CF_RELATIONS='fabric-api:requiredDependency' \
  bash scripts/curseforge-upload.sh LICENSE
```

### Confirm the Modrinth names

The Modrinth job checks the game versions and loaders against `GET /v2/tag/game_version` and `/v2/tag/loader` and
resolves the project and the Fabric API dependency (`fabric-api` = `P7dR8mSH`) before uploading, so a typo fails the
job without creating anything. In October 2026 `26.2` and `26.3` were both Modrinth release tags. The project is
looked up with the token, so a draft or unapproved project (which the public API reports as 404) works too.

```bash
curl -fsS https://api.modrinth.com/v2/tag/game_version | jq -r '.[] | select(.version | startswith("26.")) | .version'
curl -fsS -H "Authorization: $MODRINTH_TOKEN" https://api.modrinth.com/v2/project/burmaldaholic | jq '{id, slug, status}'
```

### First release

Push `v0.1.0-alpha.1` on `main` while `CURSEFORGE_PROJECT_ID` is still unset: you get a GitHub pre-release with the
jar, and the CurseForge jobs are skipped. Then set the variable and run the workflow with
`curseforge-dry-run: true` for that tag before the first real release.
