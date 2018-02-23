package resplat.mf.client.task;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

import resplat.mf.client.session.MFSession;
import resplat.mf.client.util.HasAbortableOperation;

public interface Task extends Callable<Void>, Loggable, HasProgress, HasAbortableOperation {

    public static enum State {
        PENDING, EXECUTING, COMPLETED, FAILED
    }

    State state();

    void execute(MFSession session) throws Throwable;

    String type();

    Logger logger();

}
