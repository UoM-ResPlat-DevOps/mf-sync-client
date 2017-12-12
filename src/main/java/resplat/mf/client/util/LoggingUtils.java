package resplat.mf.client.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingUtils {

    public static void log(Logger logger, Level level, String message, Throwable thrown) {
        if (logger != null) {
            logger.log(level, message, thrown);
        }
        String msg = message == null ? (thrown == null ? null : thrown.getMessage()) : message;
        if (level == null) {
            if (thrown != null) {
                System.err.println("Error: " + msg);
                thrown.printStackTrace(System.err);
            } else {
                System.out.println(msg);
            }
        } else {
            if (level.getName().equals("SEVERE")) {
                System.err.println("Error: " + msg);
                thrown.printStackTrace(System.err);
            } else {
                if (level.getName().equals("WARNING")) {
                    System.out.print("Warning: ");
                }
                System.out.println(message);
            }
        }
    }

    public static void logInfo(Logger logger, String message) {
        log(logger, Level.INFO, message, null);
    }

    public static void logWarning(Logger logger, String message) {
        log(logger, Level.WARNING, message, null);
    }

    public static void logError(Logger logger, String message, Throwable thrown) {
        log(logger, Level.SEVERE, message, thrown);
    }

    public static void logError(Logger logger, String message) {
        log(logger, Level.SEVERE, message, null);
    }

    public static void logError(Logger logger, Throwable thrown) {
        log(logger, Level.SEVERE, thrown == null ? null : thrown.getMessage(), thrown);
    }

}
