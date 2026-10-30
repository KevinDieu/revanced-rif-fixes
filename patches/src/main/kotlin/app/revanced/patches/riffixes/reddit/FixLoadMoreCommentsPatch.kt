package app.revanced.patches.riffixes.reddit

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.revanced.patches.riffixes.shared.RIF_COMPATIBILITY
import app.revanced.patches.riffixes.shared.STRING
import app.revanced.patches.riffixes.shared.THINGS
import app.revanced.patches.riffixes.shared.method
import app.revanced.patches.riffixes.shared.references
import app.revanced.patches.riffixes.shared.rifSymbols
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val COMMENT = "$THINGS/CommentThing;"
private const val THREAD = "$THINGS/ThreadThing;"


@Suppress("unused")
val fixLoadMoreCommentsPatch = bytecodePatch(
    name = "Fix load more comments",
    description = "Fixes \"load more comments\" spinning forever since Reddit blocked /api/morechildren for rif " +
        "(Oct 2026). rif loads the missing comments from /comments instead. At the bottom of huge threads " +
        "Reddit's 500-comment limit can still leave some out; rif then says so.",
) {
    compatibleWith(*RIF_COMPATIBILITY)
    dependsOn(redditResponsesPatch)

    execute {
        val symbols = rifSymbols()
        // The "load more comments" task: <init>(Activity, CommentThing more, ThreadThing thread, sort).
        val task = symbols.moreTask
        val sort = symbols.sort
        val ctor = method(task, "<init>", listOf("Landroid/app/Activity;", COMMENT, THREAD, sort), "V")
        // The first instruction loads rif's morechildren URI into a register; replace it with ours. The
        // registers used here are all set again by rif's own code before they're read.
        val load = ctor.instructions.first()
        if (load.opcode != Opcode.SGET_OBJECT || !load.references("$task->B:Landroid/net/Uri;")) {
            throw PatchException("$task constructor: expected it to start by loading the morechildren URI")
        }
        val uri = (load as OneRegisterInstruction).registerA
        if (uri != 0) throw PatchException("$task constructor: expected the URI in v0, found v$uri")
        ctor.addInstructions(
            1,
            """
                invoke-virtual { p3 }, $THREAD->getName()$STRING
                move-result-object v0
                invoke-virtual { p2 }, $COMMENT->${symbols.commentParent}()$STRING
                move-result-object v1
                invoke-virtual { p2 }, $COMMENT->${symbols.commentChildren}()[$STRING
                move-result-object v2
                invoke-static { v0, v1, v2 }, $REDDIT->moreBegin($STRING$STRING[$STRING)V
                invoke-virtual { p4 }, $sort->${symbols.sortName}()$STRING
                move-result-object v0
                invoke-static { v0 }, $REDDIT->moreUri($STRING)Landroid/net/Uri;
                move-result-object v0
            """,
        )
    }
}
