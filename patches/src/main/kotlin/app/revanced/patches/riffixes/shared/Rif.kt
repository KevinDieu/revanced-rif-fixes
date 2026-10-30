package app.revanced.patches.riffixes.shared

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/**
 * rif is fun golden platinum, the paid build. The free build is a separate R8 build with different obfuscated
 * names, which these patches don't cover.
 */
internal const val RIF_PLATINUM_PACKAGE = "com.andrewshu.android.redditdonation"

/** rif's final release. The patches use its obfuscated names, so they only apply to this version. */
internal const val RIF_VERSION = "5.6.22"

internal val RIF_COMPATIBILITY = RIF_PLATINUM_PACKAGE to setOf(RIF_VERSION)

internal const val EXTENSION = "extensions/riffixes.rve"
internal const val EXTENSION_PACKAGE = "Lapp/revanced/extension/riffixes"

internal const val STRING = "Ljava/lang/String;"

/** OkHttp's Request.Builder (okhttp3.Request$Builder), which every rif request goes through. */
internal const val REQUEST_BUILDER = "Lokhttp3/c0\$a;"

/** A method found by its class and exact signature, as obfuscated in golden platinum 5.6.22. */
internal fun methodFingerprint(type: String, name: String, parameters: List<String>, returnType: String) =
    fingerprint {
        custom { method, classDef ->
            classDef.type == type &&
                method.name == name &&
                method.returnType == returnType &&
                method.parameterTypes.map { it.toString() } == parameters
        }
    }

/** Request.Builder.url(String). */
internal val requestUrlFingerprint = methodFingerprint(REQUEST_BUILDER, "x", listOf(STRING), REQUEST_BUILDER)

/** Request.Builder.build(). */
internal val requestBuildFingerprint = methodFingerprint(REQUEST_BUILDER, "b", emptyList(), "Lokhttp3/c0;")

/** Smali that passes the request URL through [hook], a static `(String) -> String` in the extension. */
internal fun rewriteUrlSmali(hook: String) = """
    invoke-static { p1 }, $hook(${STRING})$STRING
    move-result-object p1
"""

/**
 * Switches requests to GET in Request.Builder.build() when Requests.forceGet says so (rewritten Reddit and Redgifs
 * requests). One shared hook: two separate ones in the same method got their branch targets mixed up.
 */
internal val forceGetPatch = bytecodePatch {
    compatibleWith(RIF_COMPATIBILITY)
    extendWith(EXTENSION)

    execute {
        val build = requestBuildFingerprint.method
        // Uses v0, which build() only sets later.
        build.addInstructionsWithLabels(
            0,
            """
                invoke-virtual { p0 }, $REQUEST_BUILDER->l()Lokhttp3/x;
                move-result-object v0
                invoke-static { v0 }, $EXTENSION_PACKAGE/Requests;->forceGet(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :keep_method
                invoke-virtual { p0 }, $REQUEST_BUILDER->g()$REQUEST_BUILDER
            """,
            ExternalLabel("keep_method", build.instructions.first()),
        )
    }
}

/** The index of the only instruction in this method matching [predicate]; throws if there isn't exactly one. */
internal fun MutableMethod.indexOfOnly(what: String, predicate: (Instruction) -> Boolean): Int {
    val matches = instructions.withIndex().filter { predicate(it.value) }.map { it.index }
    if (matches.size != 1) {
        throw PatchException("$definingClass->$name: expected one $what, found ${matches.size}")
    }
    return matches.single()
}

/** True if this instruction references exactly [reference] (a method, field, string or type). */
internal fun Instruction.references(reference: String) =
    (this as? ReferenceInstruction)?.reference?.toString() == reference
