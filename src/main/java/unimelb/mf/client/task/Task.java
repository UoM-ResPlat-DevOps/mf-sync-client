package unimelb.mf.client.task;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

import unimelb.mf.client.session.MFSession;
import unimelb.mf.client.util.HasAbortableOperation;

public interface Task extends Callable<Void>, Loggable, HasProgress, HasAbortableOperation {

    public static enum State {
        PENDING, EXECUTING, COMPLETED, FAILED
    }

    State state();

    void execute(MFSession session) throws Throwable;

    String type();

    Logger logger();

}
