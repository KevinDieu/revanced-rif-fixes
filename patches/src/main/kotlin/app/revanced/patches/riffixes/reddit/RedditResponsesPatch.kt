package app.revanced.patches.riffixes.reddit

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.revanced.patches.riffixes.shared.EXTENSION
import app.revanced.patches.riffixes.shared.EXTENSION_PACKAGE
import app.revanced.patches.riffixes.shared.RIF_COMPATIBILITY
import app.revanced.patches.riffixes.shared.forceGetPatch
import app.revanced.patches.riffixes.shared.indexOfOnly
import app.revanced.patches.riffixes.shared.method
import app.revanced.patches.riffixes.shared.references
import app.revanced.patches.riffixes.shared.rifSymbols
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal const val REDDIT = "$EXTENSION_PACKAGE/Reddit;"


/**
 * Shared by the gallery and "load more comments" patches. Requests they rewrite go out as GET, and their
 * responses are converted back into the shape rif expects (see Reddit.java).
 */
internal val redditResponsesPatch = bytecodePatch {
    compatibleWith(*RIF_COMPATIBILITY)
    extendWith(EXTENSION)
    dependsOn(forceGetPatch)

    execute {
        val symbols = rifSymbols()
        // rif's request task: G() sends every rif request and reads the response.
        val task = symbols.requestTask
        val run = method(task, "G", emptyList(), "Ljava/lang/Object;")

        // Once the status code is stored (iput into the task's n), the register read just before holds the
        // okhttp3 Response: remember its URL.
        val status = run.indexOfOnly("status code store") {
            it.opcode == Opcode.IPUT && it.references("$task->n:I")
        }
        val response = run.instructions.take(status).lastOrNull {
            it.opcode == Opcode.INVOKE_VIRTUAL &&
                it.references("Lokhttp3/e0;->${symbols.responseHeaders}()Lokhttp3/w;")
        } ?: throw PatchException("$task->G: response headers call not found before the status store")
        val responseRegister = (response as FiveRegisterInstruction).registerC
        run.addInstructions(status + 1, "invoke-static { v$responseRegister }, $REDDIT->response(Ljava/lang/Object;)V")

        // The body stream: ResponseBody.byteStream() then move-result-object; wrap it.
        val stream = run.indexOfOnly("body stream call") {
            it.opcode == Opcode.INVOKE_VIRTUAL && it.references("Lokhttp3/f0;->a()Ljava/io/InputStream;")
        }
        val result = run.instructions[stream + 1]
        if (result.opcode != Opcode.MOVE_RESULT_OBJECT) {
            throw PatchException("$task->G: expected move-result-object after the body stream call")
        }
        val bodyRegister = (result as OneRegisterInstruction).registerA
        run.addInstructions(
            stream + 2,
            """
                invoke-static { v$bodyRegister }, $REDDIT->body(Ljava/io/InputStream;)Ljava/io/InputStream;
                move-result-object v$bodyRegister
            """,
        )
    }
}
