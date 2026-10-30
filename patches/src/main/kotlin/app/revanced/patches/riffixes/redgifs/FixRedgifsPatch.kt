package app.revanced.patches.riffixes.redgifs

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.revanced.patches.riffixes.shared.EXTENSION
import app.revanced.patches.riffixes.shared.EXTENSION_PACKAGE
import app.revanced.patches.riffixes.shared.RIF_COMPATIBILITY
import app.revanced.patches.riffixes.shared.STRING
import app.revanced.patches.riffixes.shared.forceGetPatch
import app.revanced.patches.riffixes.shared.indexOfOnly
import app.revanced.patches.riffixes.shared.method
import app.revanced.patches.riffixes.shared.references
import app.revanced.patches.riffixes.shared.requestUrlFingerprint
import app.revanced.patches.riffixes.shared.rewriteUrlSmali
import app.revanced.patches.riffixes.shared.rifSymbols
import com.android.tools.smali.dexlib2.Opcode

private const val REDGIFS = "$EXTENSION_PACKAGE/Redgifs;"
private const val RIF_REDGIFS = "Lcom/andrewshu/android/reddit/browser/gfycat/redgifs"

// The token reply's JSON mapper: parseField(response, fieldName, parser).
private const val TOKEN = "$RIF_REDGIFS/RedgifsOAuthClientCredentialsResponseJson;"
private const val TOKEN_MAPPER = "$RIF_REDGIFS/RedgifsOAuthClientCredentialsResponseJson\$\$JsonObjectMapper;"

// Temporary tokens last about 24 hours; refresh after 23.
private const val TOKEN_LIFETIME_SECONDS = 82800

private const val FRAGMENT = "Landroidx/fragment/app/Fragment;"

@Suppress("unused")
val fixRedgifsPatch = bytecodePatch(
    name = "Fix Redgifs",
    description = "Fixes Redgifs videos failing to load: Redgifs disabled rif's API login, and rif pins " +
        "api.redgifs.com to certificates that have since been replaced. Deleted videos say so and close.",
) {
    compatibleWith(*RIF_COMPATIBILITY)
    extendWith(EXTENSION)
    dependsOn(forceGetPatch)

    execute {
        val symbols = rifSymbols()
        val parser = symbols.jsonParser

        // rif's OAuth client is disabled (401 ClientDisabled): fetch an anonymous temporary token instead.
        requestUrlFingerprint.method.addInstructions(0, rewriteUrlSmali("$REDGIFS->url"))

        // The temporary-token reply is {"token": ...} instead of {"access_token": ..., "expires_in": ...}.
        val parseField = method(TOKEN_MAPPER, "parseField", listOf(TOKEN, STRING, parser), "V")
        parseField.addInstructionsWithLabels(
            0,
            """
                const-string v0, "token"
                invoke-virtual { v0, p2 }, $STRING->equals(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :not_token
                const/4 v0, 0x0
                invoke-virtual { p3, v0 }, $parser->${symbols.jsonString}($STRING)$STRING
                move-result-object p2
                iput-object p2, p1, $TOKEN->a:$STRING
                const-wide/32 p2, $TOKEN_LIFETIME_SECONDS
                invoke-virtual { p1, p2, p3 }, $TOKEN->c(J)V
                return-void
            """,
            ExternalLabel("not_token", parseField.instructions.first()),
        )

        // Stale certificate pins: skip pinning for api.redgifs.com only. Normal certificate checks remain.
        // OkHttp's CertificatePinner.check(hostname, cleanedPeerCertificatesFn).
        val pinCheck = method("Lokhttp3/g;", "b", listOf(STRING, symbols.pinFunction), "V")
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
        // The Redgifs video loader's result T(item): null item = failed. Its HTTP status is in redgifs/a.k.
        val videoTask = symbols.videoTask
        val callback = symbols.videoCallback
        val result = method(videoTask, "T", listOf("$RIF_REDGIFS/RedgifsItem;"), "V")
        val fallback = result.indexOfOnly("failure callback") {
            it.opcode == Opcode.INVOKE_INTERFACE && it.references("$callback->${symbols.videoFailed}()V")
        }
        if (!result.instructions[fallback - 1].references("$videoTask->o:$callback")) {
            throw PatchException("$videoTask->T: expected the callback to be loaded right before its failure call")
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
                invoke-virtual { v0 }, $FRAGMENT->${symbols.fragmentActivity}()Landroidx/fragment/app/FragmentActivity;
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
