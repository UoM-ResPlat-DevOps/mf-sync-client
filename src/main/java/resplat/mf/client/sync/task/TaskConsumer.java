package resplat.mf.client.sync.task;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import resplat.mf.client.task.Task;
import resplat.mf.client.util.LoggingUtils;

public class TaskConsumer implements Runnable {

    private BlockingQueue<Task> _queue;
    private Logger _logger;
    private Task _currentTask;

    public Task currentTask() {
        return _currentTask;
    }

    public TaskConsumer(BlockingQueue<Task> queue, Logger logger) {
        _queue = queue;
        _logger = logger == null ? LoggingUtils.createConsoleLogger() : logger;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                // wait for task from queue
                _currentTask = _queue.take();
                if (_currentTask instanceof PoisonTask) {
                    _logger.info("Stopping consumer thread...");
                    break;
                }
                // execute task
                _currentTask.call();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            _logger.info("Interrupted '" + Thread.currentThread().getName() + "' thread(id="
                    + Thread.currentThread().getId() + ").");
        } catch (Throwable e) {
            _logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

}
