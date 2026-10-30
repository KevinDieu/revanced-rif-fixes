package app.revanced.extension.riffixes;

/** Hooked into OkHttp's Request.Builder.build() with its HttpUrl: true switches the request to GET. */
@SuppressWarnings("unused")
public final class Requests {
    private Requests() {
    }

    public static boolean forceGet(Object httpUrl) {
        return Reddit.forceGet(httpUrl) || Redgifs.forceGet(httpUrl);
    }
}
