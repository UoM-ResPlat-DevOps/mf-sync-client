package vicnode.mf.client.task.sync;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

import vicnode.mf.client.util.LoggingUtils;

public class TaskConsumer implements Runnable {

    private BlockingQueue<SyncTask> _queue;
    private Logger _logger;
    private SyncTask _currentTask;

    public SyncTask currentTask() {
        return _currentTask;
    }

    public TaskConsumer(BlockingQueue<SyncTask> queue, Logger logger) {
        _queue = queue;
        _logger = logger;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                // wait for task from queue
                _currentTask = _queue.take();
                // execute task
                _currentTask.call();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LoggingUtils.logInfo(_logger, "Interrupted '" + Thread.currentThread().getName() + "' thread(id="
                    + Thread.currentThread().getId() + ").");
        } catch (Throwable e) {
            LoggingUtils.logError(_logger, e);
        }
    }

}
