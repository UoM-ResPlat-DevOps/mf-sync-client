package unimelb.mf.client.task;

import java.util.logging.Level;

public interface Loggable {

    void log(Level level, String msg, Throwable thrown);

    void logError(String msg, Throwable thrown);

    void logError(String msg);

    void logError(Throwable thrown);

    void logInfo(String msg);

    void logWarning(String msg);

}
