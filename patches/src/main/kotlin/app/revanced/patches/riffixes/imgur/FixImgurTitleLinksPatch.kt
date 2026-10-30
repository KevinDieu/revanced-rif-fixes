package app.revanced.patches.riffixes.imgur

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.riffixes.shared.EXTENSION
import app.revanced.patches.riffixes.shared.EXTENSION_PACKAGE
import app.revanced.patches.riffixes.shared.RIF_COMPATIBILITY
import app.revanced.patches.riffixes.shared.STRING
import app.revanced.patches.riffixes.shared.method
import app.revanced.patches.riffixes.shared.rifSymbols


@Suppress("unused")
val fixImgurTitleLinksPatch = bytecodePatch(
    name = "Fix imgur title links",
    description = "Fixes imgur album links that include the album title (imgur.com/a/some-title-AbC123) " +
        "failing to load.",
) {
    compatibleWith(*RIF_COMPATIBILITY)
    extendWith(EXTENSION)

    // Spoof client rewrites this method too. finalize runs after every patch's execute, so the id is
    // trimmed before whatever code is there by then.
    // rif's imgur album/gallery API URL builder: static x(String albumId, boolean isGallery) -> Uri.
    execute {
        method(rifSymbols().albumUriBuilder, "x", listOf(STRING, "Z"), "Landroid/net/Uri;")
    }
    finalize {
        method(rifSymbols().albumUriBuilder, "x", listOf(STRING, "Z"), "Landroid/net/Uri;").addInstructions(
            0,
            """
                invoke-static { p0 }, $EXTENSION_PACKAGE/Imgur;->albumId($STRING)$STRING
                move-result-object p0
            """,
        )
    }
}
