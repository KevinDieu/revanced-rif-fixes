package app.revanced.patches.riffixes.reddit

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.riffixes.shared.RIF_COMPATIBILITY
import app.revanced.patches.riffixes.shared.STRING
import app.revanced.patches.riffixes.shared.methodFingerprint
import app.revanced.patches.riffixes.shared.references
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val THINGS = "Lcom/andrewshu/android/reddit/things/objects"
private const val COMMENT = "$THINGS/CommentThing;"
private const val THREAD = "$THINGS/ThreadThing;"

// The "load more comments" task (q2/b): <init>(Activity, CommentThing more, ThreadThing thread, o2/q sort).
private const val MORE_TASK = "Lq2/b;"
private const val SORT = "Lo2/q;"
private val moreTaskFingerprint = methodFingerprint(
    MORE_TASK, "<init>", listOf("Landroid/app/Activity;", COMMENT, THREAD, SORT), "V",
)

@Suppress("unused")
val fixLoadMoreCommentsPatch = bytecodePatch(
    name = "Fix load more comments",
    description = "Fixes \"load more comments\" spinning forever since Reddit blocked /api/morechildren for rif " +
        "(Oct 2026). rif loads the missing comments from /comments instead. At the bottom of huge threads " +
        "Reddit's 500-comment limit can still leave some out; rif then says so.",
) {
    compatibleWith(RIF_COMPATIBILITY)
    dependsOn(redditResponsesPatch)

    execute {
        val ctor = moreTaskFingerprint.method
        // The first instruction loads rif's morechildren URI into a register; replace it with ours. The
        // registers used here are all set again by rif's own code before they're read.
        val load = ctor.instructions.first()
        if (load.opcode != Opcode.SGET_OBJECT || !load.references("$MORE_TASK->B:Landroid/net/Uri;")) {
            throw PatchException("$MORE_TASK constructor: expected it to start by loading the morechildren URI")
        }
        val uri = (load as OneRegisterInstruction).registerA
        if (uri != 0) throw PatchException("$MORE_TASK constructor: expected the URI in v0, found v$uri")
        ctor.addInstructions(
            1,
            """
                invoke-virtual { p3 }, $THREAD->getName()$STRING
                move-result-object v0
                invoke-virtual { p2 }, $COMMENT->F()$STRING
                move-result-object v1
                invoke-virtual { p2 }, $COMMENT->z()[$STRING
                move-result-object v2
                invoke-static { v0, v1, v2 }, $REDDIT->moreBegin($STRING$STRING[$STRING)V
                invoke-virtual { p4 }, $SORT->e()$STRING
                move-result-object v0
                invoke-static { v0 }, $REDDIT->moreUri($STRING)Landroid/net/Uri;
                move-result-object v0
            """,
        )
    }
}
