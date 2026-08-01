# Publishing

Same setup as `aagya` and `dhruva`: vanniktech's maven-publish plugin to Sonatype **Central
Portal**, signed, triggered by a `v*` tag.

## One-time: repo secrets

`publish-release.yml` needs four secrets on `ksharma-xyz/darpan`. They are the same values
already configured on `ksharma-xyz/aagya`:

| Secret | What |
|---|---|
| `SONATYPE_USERNAME` | Central Portal token username |
| `SONATYPE_PASSWORD` | Central Portal token password |
| `SIGNING_KEY` | ASCII-armoured GPG private key |
| `SIGNING_KEY_PASSWORD` | passphrase for that key |

```bash
gh secret set SONATYPE_USERNAME     -R ksharma-xyz/darpan
gh secret set SONATYPE_PASSWORD     -R ksharma-xyz/darpan
gh secret set SIGNING_KEY           -R ksharma-xyz/darpan < signing-key.asc
gh secret set SIGNING_KEY_PASSWORD  -R ksharma-xyz/darpan
```

Until they exist, `publish-release.yml` fails loudly rather than silently no-opping, which is
deliberate: a tag that quietly publishes nothing is worse than a red build.

Note `publish-snapshot.yml` here is **manual only**, unlike aagya's, which runs on every push to
main. That is safe on aagya because its version carries `-SNAPSHOT` and the job exits early.
darpan is already at `0.1.0`, so the same trigger would make the first push after the secrets
land an unannounced and irreversible release.

## Releasing

1. Set `VERSION_NAME` in `gradle.properties` to the release version, no `-SNAPSHOT`.
   **Central Portal rejects SNAPSHOT versions outright** — there is no snapshot channel here.
2. Commit, push to `main`.
3. Tag and push:
   ```bash
   git tag v0.1.0 && git push origin v0.1.0
   ```
4. `publish-release.yml` runs `publishAndReleaseToMavenCentral` and cuts a GitHub Release.

## A release is permanent

Maven Central coordinates are immutable. Once `io.github.ksharma-xyz:darpan-*:0.1.0` is
released it can never be deleted, replaced, or re-uploaded. A mistake costs a version number,
not a fix.

So for the first release of a new artifact, prefer the two-step:

```bash
# Uploads a deployment to the Portal and stops. Inspect it at
# https://central.sonatype.com/publishing/deployments, then release or drop it by hand.
./gradlew publishToMavenCentral --no-configuration-cache

# Only this one is irreversible.
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

`./gradlew publishToMavenLocal` is the cheapest check of all and exercises the same POM,
module-metadata, sources and javadoc generation. CI runs it on every push.

## What consumers get

| Artifact | Targets |
|---|---|
| `io.github.ksharma-xyz:darpan-annotations` | android, jvm, iosArm64, iosSimulatorArm64, iosX64 |
| `io.github.ksharma-xyz:darpan-roborazzi` | android only (host-test classpath) |

## After the first release

Sumi currently consumes darpan as a git submodule + composite build, purely because there was
nothing published yet. Once `0.1.0` is on Central, unwind that in Sumi:

- drop `includeBuild("darpan")` from `settings.gradle.kts`
- `git submodule deinit -f darpan && git rm darpan` and delete `.gitmodules`
- drop `submodules: recursive` from `code-quality.yml`, `build-android.yml`, `build-ios.yml`
- the version catalog entries already name the real coordinates, so they do not change

Then `./qa.sh` and confirm CI is still green resolving from Central.
