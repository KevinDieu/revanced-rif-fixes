# revanced-rif-fixes

A small patch bundle for **rif is fun 5.6.22**, for use with [Morphe](https://morphe.software). rif 5.6.22 is the
last release of the discontinued Reddit client, and the bundle covers both the free app
(`com.andrewshu.android.reddit`) and **rif is fun golden platinum** (`com.andrewshu.android.redditdonation`). The
same bundle works for both.

It fixes things that broke after rif was abandoned and that other rif patches don't cover. Use it **alongside**
the rif patches built into Morphe (such as **Spoof client**). This is a Morphe port of
[notBrian2's ReVanced bundle](https://github.com/notBrian2/revanced-rif-fixes).

## Patches

| Patch | What it fixes |
|-------|---------------|
| **Fix Reddit galleries** | Galleries fail to open: since Oct 2026 Reddit answers rif's `/api/info` requests with "Blocked". rif now loads the post from `/comments` instead. |
| **Fix load more comments** | "Load more comments" spins forever: Reddit blocked `/api/morechildren` too. rif now loads the missing comments from `/comments`. Deep in very large threads Reddit only returns 500 comments per request, so the last few top-level ones may not load; rif then says so instead of spinning. |
| **Fix Redgifs** | Redgifs videos fail to load: Redgifs disabled rif's login, and rif pins `api.redgifs.com` to certificates that have since been replaced. rif now uses Redgifs' anonymous token, and skips the outdated pin for that host only (normal certificate checks still apply). Deleted videos show "This Redgifs video was removed" instead of opening the browser. |
| **Fix imgur album thumbnails** | Thumbnails for imgur albums: rif fetched them through its own server, which is gone. rif now asks imgur directly, with the key rif already sends. |
| **Fix imgur title links** | Album links with the title in them (`imgur.com/a/some-title-AbC123`) fail to load. rif now uses only the album id. |

Only version 5.6.22 is supported (free or golden platinum).

## Use with Morphe Manager

You need your own copy of rif is fun 5.6.22 (free or golden platinum). This project doesn't provide APKs.

1. Tap the button below on your phone, or add this repo as a patch source in Morphe Manager:

   ```
   https://github.com/KevinDieu/revanced-rif-fixes
   ```

   <a href="https://morphe.software/add-source?github=KevinDieu/revanced-rif-fixes">
     <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
   </a>

   Manager reads [`patches-bundle.json`](patches-bundle.json), which always points at the newest release, so
   it keeps the bundle up to date. (You can also download `patches-<version>.mpp` from
   [Releases](https://github.com/KevinDieu/revanced-rif-fixes/releases) and add it as a local file.)
2. Patch your **original, unpatched** rif 5.6.22 APK, free or golden platinum (pick it from storage, not an
   already-patched install), selecting Morphe's built-in rif patches (including **Spoof client**) and the
   patches from this bundle.
3. Install. If your current rif was patched by the same Manager, it updates in place and keeps your login.

If a patch reports that it couldn't find what it expected, the input APK isn't an untouched rif is fun 5.6.22. Every patch checks the code it changes and stops instead of patching the wrong place.

## Building from source

Requires JDK 21, the Android SDK, and a GitHub token with `read:packages` scope (Morphe publishes its
Gradle plugin and patcher to GitHub Packages). Either put credentials in `~/.gradle/gradle.properties`:

```properties
gpr.user=<github-username>
gpr.key=<token>
githubPackagesUsername=<github-username>
githubPackagesPassword=<token>
```

or pass them as environment variables (`GITHUB_ACTOR`, `GITHUB_TOKEN`,
`ORG_GRADLE_PROJECT_githubPackagesUsername`, `ORG_GRADLE_PROJECT_githubPackagesPassword`). Then:

```bash
./gradlew buildAndroid
# output: patches/build/libs/patches-<version>.mpp
```

`buildAndroid` is required: plain `./gradlew build` makes a bundle the desktop CLI can load but Morphe
Manager can't.

To release, bump `version` in `gradle.properties` and push a matching tag (`v2.0.0` for `version = 2.0.0`).
The Build workflow then publishes the release with its `.mpp` and points `patches-bundle.json` on `main` at it.
Every other push is just built, to check it compiles.

Notes:
- The patch code lives in `app.revanced.patches.riffixes` and the extension in `extensions/riffixes`, so
  nothing clashes with other rif bundles loaded beside it.
- The Java extension (`extensions/riffixes`) holds the logic. The Kotlin patches only hook it into rif.
- rif is R8-obfuscated, and the free and golden platinum builds are obfuscated separately: same code, different
  names. The names each patch needs are in `RIF_BUILDS` (`patches/src/main/kotlin/app/revanced/patches/riffixes/shared/Rif.kt`),
  and the patches work out which app they're patching from its code.

## Disclaimer

Not affiliated with or endorsed by Andrew Shu, rif is fun, Reddit, imgur, Redgifs, ReVanced or Morphe. This
repository contains only original patch code: no APKs, no code from rif. You are responsible for using your
own legally obtained copy of the app and for following the terms of the services it connects to. Use at your
own risk.

## License

GNU General Public License v3.0, see [LICENSE](LICENSE).
