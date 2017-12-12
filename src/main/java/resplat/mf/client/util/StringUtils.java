package resplat.mf.client.util;

public class StringUtils {

    public static String trimPrefix(String str, String prefix, boolean repeat) {
        String r = str;
        if (repeat) {
            while (r.startsWith(prefix)) {
                r = r.substring(prefix.length());
            }
        } else {
            if (r.startsWith(prefix)) {
                r = r.substring(prefix.length());
            }
        }
        return r;
    }

    public static String trimSuffix(String str, String suffix, boolean repeat) {
        String r = str;
        if (repeat) {
            while (r.endsWith(suffix)) {
                r = r.substring(0, r.length() - suffix.length());
            }
        } else {
            if (r.endsWith(suffix)) {
                r = r.substring(0, r.length() - suffix.length());
            }
        }
        return r;
    }

}
