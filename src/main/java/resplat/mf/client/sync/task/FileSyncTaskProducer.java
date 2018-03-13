package resplat.mf.client.sync.task;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import resplat.mf.client.file.Filter;
import resplat.mf.client.session.MFSession;
import resplat.mf.client.sync.MFSyncSettings;
import resplat.mf.client.task.Task;

public class FileSyncTaskProducer implements Runnable {

    private MFSession _session;
    private Logger _logger;
    private BlockingQueue<Task> _queue;

    private ThreadPoolExecutor _checkThreadPool;
    private BlockingQueue<FileUploadTask> _checkQueue;

    private MFSyncSettings _settings;
    private FileUploadListener _ul;

    private Filter _filter = null;

    public FileSyncTaskProducer(MFSession session, Logger logger, MFSyncSettings settings, FileUploadListener ul,
            BlockingQueue<Task> queue) {
        _session = session;
        _logger = logger;
        _settings = settings;
        _ul = ul;
        _queue = queue;
        _checkThreadPool = new ThreadPoolExecutor(1, _settings.maxNumberOfCheckers(), 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "Checker");
                    }
                }, new ThreadPoolExecutor.CallerRunsPolicy());
        _checkQueue = new LinkedBlockingQueue<FileUploadTask>();
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                _logger.info("Start scanning source files...");
                execute();
                if (!_settings.daemonEnabled()) {
                    _checkThreadPool.shutdown();
                    _checkThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
                    int nbWorkers = _settings.numberOfWorkers();
                    for (int i = 0; i < nbWorkers; i++) {
                        _queue.put(new PoisonTask());
                    }
                    break;
                }
                Thread.sleep(_settings.daemonScanInterval());
                while (_checkThreadPool.getActiveCount() > 0 || !_checkQueue.isEmpty() || !_queue.isEmpty()) {
                    _logger.info("Task queue is not empty. Wait for " + (_settings.daemonScanInterval() / 1000)
                            + " seconds...");
                    Thread.sleep(_settings.daemonScanInterval());
                }
            }
        } catch (Throwable e) {
            if (_logger != null) {
                if (e instanceof InterruptedException) {
                    _checkThreadPool.shutdownNow();
                    _logger.info("Thread interrupted. Stopped scanning source files.");
                } else {
                    _logger.log(Level.SEVERE, e.getMessage(), e);
                }
            }
        }
    }

    public FileSyncTaskProducer setFilter(Filter filter) {
        _filter = filter;
        return this;
    }

    protected void execute() throws Throwable {
        List<MFSyncSettings.Job> jobs = _settings.jobs();
        for (MFSyncSettings.Job job : jobs) {
            submit(job);
        }
    }

    private void submit(MFSyncSettings.Job job) throws Throwable {
        Files.walkFileTree(job.directory(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    if (_filter == null || _filter.acceptFile(file, attrs)) {
                        if (job.matchPath(file)) {
                            _checkQueue.put(new FileUploadTask(_session, _logger, file, job.directory(),
                                    job.namespace(), _settings.csumCheck(), _ul));
                            if (_checkQueue.size() >= _settings.checkBatchSize()) {
                                List<FileUploadTask> tasks = new ArrayList<FileUploadTask>();
                                int nbTasks = _checkQueue.drainTo(tasks, _settings.checkBatchSize());
                                if (!tasks.isEmpty()) {
                                    _logger.info("Submitting " + nbTasks + " files to check...");
                                    _checkThreadPool.submit(new FileCheckTask(_session, _logger, tasks, _queue, _ul));
                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        return FileVisitResult.TERMINATE;
                    }
                    if (_logger != null) {
                        _logger.log(Level.SEVERE, e.getMessage(), e);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException ioe) {
                _logger.log(Level.SEVERE, "Failed to access file: " + file, ioe);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException ioe) {
                if (ioe != null) {
                    _logger.log(Level.SEVERE, ioe.getMessage(), ioe);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (_filter == null || _filter.acceptDirectory(dir, attrs)) {
                    try {
                        if (!_settings.excludeEmptyFolder()) {
                            if (job.matchPath(dir)) {
                                _queue.put(new AssetNamespaceCreateTask(_session, _logger, dir, job.directory(),
                                        job.namespace()));
                            }
                        }
                    } catch (Throwable e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                            return FileVisitResult.TERMINATE;
                        }
                        _logger.log(Level.SEVERE, e.getMessage(), e);
                    }
                    return FileVisitResult.CONTINUE;
                } else {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
        });
        while (!_checkQueue.isEmpty()) {
            List<FileUploadTask> tasks = new ArrayList<FileUploadTask>();
            int nbTasks = _checkQueue.drainTo(tasks, _settings.checkBatchSize());
            if (!tasks.isEmpty()) {
                _logger.info("Submitting " + nbTasks + " files to check...");
                _checkThreadPool.submit(new FileCheckTask(_session, _logger, tasks, _queue, _ul));
            }
        }
    }

}
