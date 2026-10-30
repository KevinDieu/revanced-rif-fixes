"""Rewrite patches-bundle.json from this repo's newest GitHub release.

Morphe Manager adds this repo as a patch source by its GitHub URL and reads patches-bundle.json from the root of
main, which points at the release's patches-<version>.mpp. The Build workflow runs this after publishing a
release; to run it by hand:

    python scripts/update_bundle_json.py

Standard library only; GitHub's public API needs no token.
"""

import json
import pathlib
import urllib.request

REPO = "KevinDieu/revanced-rif-fixes"
BUNDLE = pathlib.Path(__file__).resolve().parent.parent / "patches-bundle.json"


def bundle_asset(release):
    return next((a for a in release["assets"] if a["name"].endswith(".mpp")), None)


def newest_release():
    request = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/releases?per_page=100",
        headers={"Accept": "application/vnd.github+json", "User-Agent": REPO},
    )
    with urllib.request.urlopen(request) as response:
        found = json.load(response)
    found = [r for r in found if not r["draft"] and not r["prerelease"] and bundle_asset(r)]
    return max(found, key=lambda r: r["published_at"], default=None)


def main():
    release = newest_release()
    if release is None:
        print("no release with a .mpp bundle yet, left unchanged")
        return
    entry = {
        "created_at": release["published_at"].rstrip("Z"),
        "description": release["body"] or "",
        "download_url": bundle_asset(release)["browser_download_url"],
        "signature_download_url": "",
        "version": release["tag_name"],
    }
    BUNDLE.write_text(json.dumps(entry, indent=2) + "\n", encoding="utf-8")
    print(f"patches-bundle.json: {release['tag_name']}")


if __name__ == "__main__":
    main()
