package vicnode.mf.client.task;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

import arc.mf.client.ServerClient;

public interface Task extends Callable<Void>, Loggable, HasProgress {

    public static enum State {
        PENDING, EXECUTING, COMPLETED, FAILED
    }

    State state();

    ServerClient.Connection connect();

    void execute(ServerClient.Connection cxn) throws Throwable;

    String type();

    Logger logger();

}
