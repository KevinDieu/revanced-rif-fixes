package app.revanced.extension.riffixes;

import android.app.Activity;
import android.widget.Toast;

/**
 * Redgifs disabled rif's OAuth client (401 ClientDisabled), so rif fetches an anonymous temporary token instead.
 * rif also pins api.redgifs.com to certificates Redgifs has since replaced.
 */
@SuppressWarnings("unused")
public final class Redgifs {
    private static final String OAUTH_CLIENT = "https://api.redgifs.com/v2/oauth/client";
    private static final String TEMPORARY = "https://api.redgifs.com/v2/auth/temporary";

    private Redgifs() {
    }

    /** Hooked into OkHttp's Request.Builder.url(String). */
    public static String url(String url) {
        return OAUTH_CLIENT.equals(url) ? TEMPORARY : url;
    }

    /** Hooked into Request.Builder.build(): the temporary-token endpoint is GET (rif POSTs a form). */
    public static boolean forceGet(Object httpUrl) {
        return TEMPORARY.equals(String.valueOf(httpUrl));
    }

    /** Hooked into OkHttp's certificate pinner: skip rif's stale pins for this host only. */
    public static boolean skipPin(String host) {
        return "api.redgifs.com".equals(host);
    }

    /**
     * Hooked into the Redgifs video loader's failure path when Redgifs answered 404 (video deleted): say so and
     * close the viewer instead of falling back to the browser.
     */
    public static boolean removed(Activity activity) {
        if (activity == null) {
            return false;
        }
        Toast.makeText(activity, "This Redgifs video was removed", Toast.LENGTH_LONG).show();
        activity.onBackPressed();
        return true;
    }
}
