# Contributing to Burmaldaholic

Burmaldaholic has two parts: the Java-edition Fabric mod in `java/` and the Bedrock add-on in `bedrock/`. Both are
built, tested and released by GitHub Actions from this repository.

## Branch flow

`main` is always releasable, and nobody commits to it directly.

```
main ──●──────────●────────────●──── tag v1.3.0 → release
        \        / squash     /
         feature/slots   fix/roulette-odds
```

### `main` is protected

- Changes land only through a pull request.
- The `ci-ok` check must pass. It aggregates every CI job (branch name, actionlint, scripts, the Java builds for
  Minecraft 26.2 and 26.3, the Bedrock build); edition builds a PR does not touch are skipped and count as passed.
- PRs are **squash-merged**, so history stays linear: one commit per PR.
- No force-pushes to `main`.

### Branch names

Name your branch `<type>/<kebab-name>`, for example `feature/slots` or `fix/roulette-odds`. The `branch-name`
check rejects other names (`dependabot/`, `renovate/` and `claude/` branches are allowed as they are).

| Type | Use for | PR label → release-notes section |
|---|---|---|
| `feature/`, `feat/` | new functionality | `enhancement` → New features |
| `fix/`, `hotfix/` | bug fixes | `bug` → Bug fixes |
| `docs/` | documentation only | `documentation` → Documentation |
| `chore/`, `ci/`, `build/`, `refactor/`, `perf/`, `test/` | everything else | `chore` → Maintenance |
| `release/` | release preparation | (none) → Other changes |

The prefix sets the PR label automatically, and the label decides where the PR appears in the release notes. PRs that
touch `java/` or `bedrock/` also get a `java` / `bedrock` label.

### PR titles

The PR title becomes a line in the release notes, and from there in the CurseForge changelog. Write it as an imperative
sentence that makes sense to players: "Add roulette table", not "roulette wip".

- Add the `breaking` label to a PR that breaks worlds, configs or compatibility. It gets its own section at the top.
- Add `skip-changelog` to leave a PR out of the notes (for example a typo fix in CI).

### Releases

- Only maintainers create releases.
- A release is an annotated tag on a commit of `main`: `vMAJOR.MINOR.PATCH`, optionally with `-alpha.N`, `-beta.N` or
  `-rc.N` (no `+build` suffix). A tag on any other branch is rejected.
- Everything after the tag is automated: the jar and the `.mcaddon` are built and attached to a GitHub release, then
  uploaded to CurseForge.
- The version lives **only in the tag**. Do not bump `mod_version` in `java/gradle.properties` or `version` in
  `bedrock/package.json` to release; the manifests are generated at build time.

Details: [docs/ci/RELEASING.md](docs/ci/RELEASING.md).

## Local checks before opening a PR

Run the same checks as CI:

```bash
(cd java && ./gradlew build -Pmc=26.2 && ./gradlew build -Pmc=26.3)      # JDK 25; compiles, tests, gametests, builds the jar
(cd bedrock && npm ci && npm run lint && npm test && npm run build)       # Node 22; builds dist/Burmaldaholic-<version>.mcaddon
```

If you changed `.github/` or `scripts/`:

```bash
shellcheck scripts/*.sh scripts/test/*.sh
bash scripts/test/curseforge-upload.test.sh
bash scripts/check-inlined-script.sh
actionlint && actionlint .github/templates/release-caller.yml             # https://github.com/rhysd/actionlint
```

`scripts/curseforge-upload.sh` has a byte-identical copy inside
`.github/workflows/reusable-publish-curseforge.yml`. Edit the script, then paste it into the workflow's heredoc;
`check-inlined-script.sh` tells you when they differ.

## CI and release pipeline

- `.github/workflows/ci.yml` runs on every PR and on `main`.
- `.github/workflows/release.yml` runs on version tags.
- The `reusable-*.yml` workflows, `scripts/` and `.github/templates/` are shared, byte-identical, with
  `nezo32/enchantaholic`; change them in both repositories (or, better, move them to a shared repository). See
  [docs/ci/REUSABLE_RELEASE_PIPELINE.md](docs/ci/REUSABLE_RELEASE_PIPELINE.md).
