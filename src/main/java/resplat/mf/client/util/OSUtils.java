package resplat.mf.client.util;

public class OSUtils {

    public static boolean isWindows() {
        return System.getProperty("os.name", "generic").toLowerCase().indexOf("windows") >= 0;
    }

    public static final boolean IS_WINDOWS = isWindows();

}
