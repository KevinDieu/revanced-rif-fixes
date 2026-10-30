package app.revanced.patches.riffixes.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/** rif is fun, the free ad-supported app. */
internal const val RIF_FREE_PACKAGE = "com.andrewshu.android.reddit"

/** rif is fun golden platinum, the paid app: the same code as a separate R8 build, so most names differ. */
internal const val RIF_PLATINUM_PACKAGE = "com.andrewshu.android.redditdonation"

/** rif's final release. The patches use its obfuscated names, so they only apply to this version. */
internal const val RIF_VERSION = "5.6.22"

internal val RIF_COMPATIBILITY = arrayOf(
    Compatibility(
        packageName = RIF_FREE_PACKAGE,
        name = "rif is fun",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = RIF_VERSION)),
    ),
    Compatibility(
        packageName = RIF_PLATINUM_PACKAGE,
        name = "rif is fun golden platinum",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = RIF_VERSION)),
    ),
)

/**
 * Obfuscated names that differ between the free and golden platinum builds of rif 5.6.22. The code at each spot
 * is the same in both, and every patch still checks the instructions it expects, so a wrong name fails the patch
 * instead of the app.
 */
internal class RifSymbols(
    val build: String,
    /** Request task that sends every rif request: `G()` stores the status in `n:I` and reads the body. */
    val requestTask: String,
    /** okhttp3.Response method returning its headers, called right before the status is stored. */
    val responseHeaders: String,
    /** "Load more comments" task; its constructor's first instruction loads `B:Uri`. */
    val moreTask: String,
    /** Comment sort enum (4th constructor parameter of [moreTask]) and its `()String` API name getter. */
    val sort: String,
    val sortName: String,
    /** CommentThing getters: parent id `()String`, child ids `()String[]`. */
    val commentParent: String,
    val commentChildren: String,
    /** Class with the imgur album URL builder, static `x(String, boolean) -> Uri`. */
    val albumUriBuilder: String,
    /** LoganSquare JSON parser and its `(String default) -> String` value getter. */
    val jsonParser: String,
    val jsonString: String,
    /** Function type of OkHttp CertificatePinner.check(hostname, cleanedPeerCertificatesFn). */
    val pinFunction: String,
    /** Redgifs video loader result class, its callback interface and the callback's "failed" method. */
    val videoTask: String,
    val videoCallback: String,
    val videoFailed: String,
    /** Fragment.getActivity() (the non-throwing one). */
    val fragmentActivity: String,
    /** ImgurV3ImageItem id getter. */
    val imageId: String,
)

internal val RIF_BUILDS = listOf(
    RifSymbols(
        build = "golden platinum", requestTask = "Li3/c;", responseHeaders = "C", moreTask = "Lq2/b;",
        sort = "Lo2/q;", sortName = "e", commentParent = "F", commentChildren = "z", albumUriBuilder = "Lh2/c;",
        jsonParser = "Lw6/h;", jsonString = "c0", pinFunction = "Lpe/a;", videoTask = "Lb2/d\$b;",
        videoCallback = "Lw1/y;", videoFailed = "H", fragmentActivity = "o1", imageId = "l",
    ),
    RifSymbols(
        build = "free", requestTask = "Lh3/c;", responseHeaders = "E", moreTask = "Lp2/b;",
        sort = "Ln2/q;", sortName = "d", commentParent = "K", commentChildren = "w", albumUriBuilder = "Lg2/c;",
        jsonParser = "Lx6/h;", jsonString = "e0", pinFunction = "Lof/a;", videoTask = "La2/d\$b;",
        videoCallback = "Lv1/y;", videoFailed = "O", fragmentActivity = "z1", imageId = "n",
    ),
)

internal const val THINGS = "Lcom/andrewshu/android/reddit/things/objects"

/** The symbol set of the app being patched, told apart by the "load more comments" task's constructor. */
internal fun BytecodePatchContext.rifSymbols(): RifSymbols {
    val matching = RIF_BUILDS.filter { symbols ->
        classDefByOrNull(symbols.moreTask)?.methods?.any { method ->
            method.name == "<init>" && method.parameterTypes.map { it.toString() } == listOf(
                "Landroid/app/Activity;", "$THINGS/CommentThing;", "$THINGS/ThreadThing;", symbols.sort,
            )
        } == true
    }
    return matching.singleOrNull()
        ?: throw PatchException("Not rif is fun 5.6.22 (free or golden platinum): matched ${matching.size} builds")
}

/** A method by its class and exact signature; throws if it isn't there. */
internal fun BytecodePatchContext.method(
    type: String,
    name: String,
    parameters: List<String>,
    returnType: String,
): MutableMethod {
    val classDef = mutableClassDefByOrNull(type) ?: throw PatchException("$type not found")
    return classDef.methods.firstOrNull { method ->
        method.name == name &&
            method.returnType == returnType &&
            method.parameterTypes.map { it.toString() } == parameters
    } ?: throw PatchException("$type->$name(${parameters.joinToString("")})$returnType not found")
}

internal const val EXTENSION = "extensions/riffixes.mpe"
internal const val EXTENSION_PACKAGE = "Lapp/revanced/extension/riffixes"

internal const val STRING = "Ljava/lang/String;"

/** OkHttp's Request.Builder (okhttp3.Request$Builder), which every rif request goes through. */
internal const val REQUEST_BUILDER = "Lokhttp3/c0\$a;"

/** A method found by its class and exact signature (OkHttp's request builder has the same names in both builds). */
internal fun methodFingerprint(type: String, name: String, parameters: List<String>, returnType: String) =
    Fingerprint(
        definingClass = type,
        name = name,
        custom = { method, classDef ->
            classDef.type == type &&
                method.returnType == returnType &&
                method.parameterTypes.map { it.toString() } == parameters
        },
    )

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
    compatibleWith(*RIF_COMPATIBILITY)
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
