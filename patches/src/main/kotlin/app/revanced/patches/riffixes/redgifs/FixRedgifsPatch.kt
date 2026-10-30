package app.revanced.patches.riffixes.redgifs

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.riffixes.shared.EXTENSION
import app.revanced.patches.riffixes.shared.EXTENSION_PACKAGE
import app.revanced.patches.riffixes.shared.RIF_COMPATIBILITY
import app.revanced.patches.riffixes.shared.STRING
import app.revanced.patches.riffixes.shared.forceGetPatch
import app.revanced.patches.riffixes.shared.indexOfOnly
import app.revanced.patches.riffixes.shared.methodFingerprint
import app.revanced.patches.riffixes.shared.references
import app.revanced.patches.riffixes.shared.requestUrlFingerprint
import app.revanced.patches.riffixes.shared.rewriteUrlSmali
import com.android.tools.smali.dexlib2.Opcode

private const val REDGIFS = "$EXTENSION_PACKAGE/Redgifs;"
private const val RIF_REDGIFS = "Lcom/andrewshu/android/reddit/browser/gfycat/redgifs"

// The token reply's JSON mapper: parseField(response, fieldName, parser).
private const val TOKEN = "$RIF_REDGIFS/RedgifsOAuthClientCredentialsResponseJson;"
private const val JSON_PARSER = "Lw6/h;"
private val tokenParseFieldFingerprint = methodFingerprint(
    "$RIF_REDGIFS/RedgifsOAuthClientCredentialsResponseJson\$\$JsonObjectMapper;",
    "parseField", listOf(TOKEN, STRING, JSON_PARSER), "V",
)

// Temporary tokens last about 24 hours; refresh after 23.
private const val TOKEN_LIFETIME_SECONDS = 82800

// OkHttp's CertificatePinner.check(hostname, cleanedPeerCertificatesFn).
private val pinCheckFingerprint = methodFingerprint("Lokhttp3/g;", "b", listOf(STRING, "Lpe/a;"), "V")

// The Redgifs video loader's result (b2/d$b.T): null item = failed. Its HTTP status is in redgifs/a.k.
private const val VIDEO_TASK = "Lb2/d\$b;"
private val videoResultFingerprint = methodFingerprint(VIDEO_TASK, "T", listOf("$RIF_REDGIFS/RedgifsItem;"), "V")
private const val FRAGMENT = "Landroidx/fragment/app/Fragment;"

@Suppress("unused")
val fixRedgifsPatch = bytecodePatch(
    name = "Fix Redgifs",
    description = "Fixes Redgifs videos failing to load: Redgifs disabled rif's API login, and rif pins " +
        "api.redgifs.com to certificates that have since been replaced. Deleted videos say so and close.",
) {
    compatibleWith(RIF_COMPATIBILITY)
    extendWith(EXTENSION)
    dependsOn(forceGetPatch)

    execute {
        // rif's OAuth client is disabled (401 ClientDisabled): fetch an anonymous temporary token instead.
        requestUrlFingerprint.method.addInstructions(0, rewriteUrlSmali("$REDGIFS->url"))

        // The temporary-token reply is {"token": ...} instead of {"access_token": ..., "expires_in": ...}.
        val parseField = tokenParseFieldFingerprint.method
        parseField.addInstructionsWithLabels(
            0,
            """
                const-string v0, "token"
                invoke-virtual { v0, p2 }, $STRING->equals(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :not_token
                const/4 v0, 0x0
                invoke-virtual { p3, v0 }, $JSON_PARSER->c0($STRING)$STRING
                move-result-object p2
                iput-object p2, p1, $TOKEN->a:$STRING
                const-wide/32 p2, $TOKEN_LIFETIME_SECONDS
                invoke-virtual { p1, p2, p3 }, $TOKEN->c(J)V
                return-void
            """,
            ExternalLabel("not_token", parseField.instructions.first()),
        )

        // Stale certificate pins: skip pinning for api.redgifs.com only. Normal certificate checks remain.
        val pinCheck = pinCheckFingerprint.method
        pinCheck.addInstructionsWithLabels(
            0,
            """
                invoke-static { p1 }, $REDGIFS->skipPin($STRING)Z
                move-result v0
                if-eqz v0, :check_pins
                return-void
            """,
            ExternalLabel("check_pins", pinCheck.instructions.first()),
        )

        // Failed load: if Redgifs answered 404, say the video was removed and close the viewer instead of
        // falling back to the browser. p1 is the viewer callback (a Fragment) right before rif's H() call.
        val result = videoResultFingerprint.method
        val fallback = result.indexOfOnly("failure callback") {
            it.opcode == Opcode.INVOKE_INTERFACE && it.references("Lw1/y;->H()V")
        }
        if (!result.instructions[fallback - 1].references("$VIDEO_TASK->o:Lw1/y;")) {
            throw PatchException("$VIDEO_TASK->T: expected the callback to be loaded right before H()")
        }
        result.addInstructionsWithLabels(
            fallback,
            """
                iget v1, p0, $RIF_REDGIFS/a;->k:I
                const/16 v0, 0x194
                if-ne v1, v0, :not_removed
                instance-of v0, p1, $FRAGMENT
                if-eqz v0, :not_removed
                move-object v0, p1
                check-cast v0, $FRAGMENT
                invoke-virtual { v0 }, $FRAGMENT->o1()Landroidx/fragment/app/FragmentActivity;
                move-result-object v0
                invoke-static { v0 }, $REDGIFS->removed(Landroid/app/Activity;)Z
                move-result v0
                if-eqz v0, :not_removed
                return-void
            """,
            ExternalLabel("not_removed", result.instructions[fallback]),
        )
    }
}
