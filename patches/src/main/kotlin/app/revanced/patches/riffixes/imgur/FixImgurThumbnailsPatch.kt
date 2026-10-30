package app.revanced.patches.riffixes.imgur

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.riffixes.shared.EXTENSION
import app.revanced.patches.riffixes.shared.EXTENSION_PACKAGE
import app.revanced.patches.riffixes.shared.RIF_COMPATIBILITY
import app.revanced.patches.riffixes.shared.STRING
import app.revanced.patches.riffixes.shared.methodFingerprint
import app.revanced.patches.riffixes.shared.requestUrlFingerprint
import app.revanced.patches.riffixes.shared.rewriteUrlSmali
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val IMGUR = "Lcom/andrewshu/android/reddit/imgur"
private const val ALBUM = "$IMGUR/ImgurV3Album;"
private const val ITEM = "$IMGUR/ImgurV3ImageItem;"

private val albumParseFingerprint = methodFingerprint(
    "$IMGUR/ImgurV3Album\$\$JsonObjectMapper;", "parse", listOf("Lw6/h;"), ALBUM,
)

@Suppress("unused")
val fixImgurThumbnailsPatch = bytecodePatch(
    name = "Fix imgur album thumbnails",
    description = "Fixes imgur album thumbnails failing to load: rif fetched them through its own imgur proxy, " +
        "which no longer exists.",
) {
    compatibleWith(RIF_COMPATIBILITY)
    extendWith(EXTENSION)

    execute {
        requestUrlFingerprint.method.addInstructions(0, rewriteUrlSmali("$EXTENSION_PACKAGE/Imgur;->proxyUrl"))

        // imgur's /3/album/<id> often has an empty cover: fall back to the first image when parsing.
        val parse = albumParseFingerprint.method
        val returns = parse.instructions.withIndex().filter {
            it.value.opcode == Opcode.RETURN_OBJECT && (it.value as OneRegisterInstruction).registerA == 0
        }
        if (returns.size != 1) throw PatchException("$ALBUM parse: expected one return of the album, found ${returns.size}")
        val ret = returns.single().index
        // Only branches reach that return, so keep its location (and their label) for the first new instruction,
        // and return from a new instruction after the fallback.
        parse.addInstruction(ret + 1, "return-object v0")
        val done = parse.instructions[ret + 1]
        parse.replaceInstruction(ret, "invoke-virtual { v0 }, $ALBUM->b()$STRING")
        parse.addInstructionsWithLabels(
            ret + 1,
            """
                move-result-object v1
                invoke-static { v1 }, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                move-result v1
                if-eqz v1, :done
                invoke-virtual { v0 }, $ALBUM->h()[$ITEM
                move-result-object v1
                if-eqz v1, :done
                array-length v2, v1
                if-eqz v2, :done
                const/4 v2, 0x0
                aget-object v1, v1, v2
                invoke-virtual { v1 }, $ITEM->l()$STRING
                move-result-object v1
                invoke-virtual { v0, v1 }, $ALBUM->l($STRING)V
            """,
            ExternalLabel("done", done),
        )
    }
}
