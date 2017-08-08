package vicnode.mf.client.util;

import java.nio.file.Path;

public class PathUtils {

    public static final String SLASH = "/";

    public static final char SLASH_CHAR = '/';

    public static final char BACKSLASH_CHAR = '\\';

    public static String trimLeadingSlash(String str, String slash) {
        return StringUtils.trimPrefix(str, slash, true);
    }

    public static String trimLeadingSlash(String str) {
        return StringUtils.trimPrefix(str, SLASH, true);
    }

    public static String trimTrailingSlash(String str, String slash) {
        return StringUtils.trimSuffix(str, slash, true);
    }

    public static String trimTrailingSlash(String str) {
        return StringUtils.trimSuffix(str, SLASH, true);
    }

    public static String trimSlash(String str) {
        return trimLeadingSlash(trimTrailingSlash(str));
    }

    public static String join(String path1, String path2, String... paths) {

        StringBuilder sb = new StringBuilder();
        boolean leadingSlash = false;
        if (path1 != null) {
            leadingSlash = path1.startsWith(SLASH);
            sb.append(SLASH);
            sb.append(trimSlash(path1));
        }
        if (path2 != null) {
            sb.append(SLASH);
            sb.append(trimSlash(path2));
        }
        if (paths != null) {
            for (String p : paths) {
                if (p != null) {
                    sb.append(SLASH);
                    sb.append(trimSlash(p));
                }
            }
        }
        if (sb.length() <= 0) {
            return null;
        }
        if (leadingSlash) {
            return sb.toString();
        } else {
            return trimLeadingSlash(sb.toString());
        }
    }

    public static String getParentPath(String path) {
        if (path == null) {
            return null;
        }
        boolean leadingSlash = path.startsWith(SLASH);
        String p = trimSlash(path);
        int idx = p.lastIndexOf(SLASH);
        if (idx < 0) {
            if (leadingSlash) {
                return SLASH + p;
            } else {
                return p;
            }
        } else {
            if (leadingSlash) {
                return SLASH + (p.substring(0, idx));
            } else {
                return p.substring(0, idx);
            }
        }
    }

    public static String getLastComponent(String path) {
        if (path == null) {
            return null;
        }
        String p = trimLeadingSlash(trimTrailingSlash(path));
        int idx = p.lastIndexOf(SLASH_CHAR);
        if (idx == -1) {
            return p;
        }
        return p.substring(idx + 1);
    }

    public static String getFileExtension(String path) {
        if (path != null && !path.endsWith(SLASH)) {
            int idx = path.indexOf('.');
            if (idx != -1) {
                return path.substring(idx + 1);
            }
        }
        return null;
    }

    public static boolean isOrIsDescendant(Path a, Path b) {
        return isOrIsDescendant(a == null ? null : a.toAbsolutePath().toString(),
                b == null ? null : b.toAbsolutePath().toString());
    }

    public static boolean isOrIsDescendant(String a, String b) {
        String sa = b == null ? null : normalise(b);
        String sb = a == null ? null : normalise(a);
        if (sa != null && sb != null) {
            return sb.startsWith(sa + SLASH);
        }
        return false;
    }

    public static String normalise(String path) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("windows") != -1;
        if (path == null) {
            return null;
        }
        if (isWindows) {
            return trimTrailingSlash(path.replace(BACKSLASH_CHAR, SLASH_CHAR));
        } else {
            return trimTrailingSlash(path);
        }
    }

//    public static void main(String[] args) throws Throwable {
//        System.out.println(Paths.get("a/b/c").relativize(Paths.get("a/b/c/d")));
//    }

}
