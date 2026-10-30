package app.revanced.patches.riffixes.reddit

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.riffixes.shared.RIF_COMPATIBILITY
import app.revanced.patches.riffixes.shared.requestUrlFingerprint
import app.revanced.patches.riffixes.shared.rewriteUrlSmali

@Suppress("unused")
val fixGalleriesPatch = bytecodePatch(
    name = "Fix Reddit galleries",
    description = "Fixes Reddit galleries failing to open since Reddit blocked /api/info for rif (Oct 2026). " +
        "rif loads the post from /comments instead.",
) {
    compatibleWith(RIF_COMPATIBILITY)
    dependsOn(redditResponsesPatch)

    execute {
        requestUrlFingerprint.method.addInstructions(0, rewriteUrlSmali("$REDDIT->url"))
    }
}
