package app.revanced.extension.riffixes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Newer imgur links put the title in the path (/a/some-album-title-qeqfMzC). imgur's API only takes the id, which
 * is the part after the last dash; plain ids never contain one.
 *
 * rif's album thumbnails still go through its own imgur proxy, api.redditisfun.com, which is gone. rif already
 * sends its imgur Client-ID with them, so they work against api.imgur.com directly.
 */
@SuppressWarnings("unused")
public final class Imgur {
    private static final String PROXY = "https://api.redditisfun.com/api/imgur/";
    private static final String API = "https://api.imgur.com/";
    // /3/album/<id>/cover only existed on the proxy; /3/album/<id> has the same fields.
    private static final Pattern ALBUM_COVER = Pattern.compile("^(https://api\\.imgur\\.com/3/album/)([^/?]+)/cover$");

    private Imgur() {
    }

    /** Hooked into OkHttp's Request.Builder.url(String). */
    public static String proxyUrl(String url) {
        if (url == null || !url.startsWith(PROXY)) {
            return url;
        }
        url = API + url.substring(PROXY.length());
        Matcher m = ALBUM_COVER.matcher(url);
        return m.matches() ? m.group(1) + albumId(m.group(2)) : url;
    }

    /** Hooked into the start of rif's album URL builder. */
    public static String albumId(String id) {
        if (id != null && id.lastIndexOf('-') >= 0) {
            return id.substring(id.lastIndexOf('-') + 1);
        }
        return id;
    }
}
