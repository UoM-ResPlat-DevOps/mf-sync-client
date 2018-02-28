package resplat.mf.client.sync.task;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import resplat.mf.client.file.Filter;
import resplat.mf.client.session.MFSession;
import resplat.mf.client.sync.MFSyncSettings;

public class FileSyncTaskProducer implements Runnable {

    private MFSession _session;
    private Logger _logger;
    private BlockingQueue<SyncTask> _queue;

    private MFSyncSettings _settings;
    private FileUploadListener _ul;

    private Filter _filter = null;;

    public FileSyncTaskProducer(MFSession session, Logger logger, MFSyncSettings settings, FileUploadListener ul,
            BlockingQueue<SyncTask> queue) {
        _session = session;
        _logger = logger;
        _settings = settings;
        _ul = ul;
        _queue = queue;
    }

    @Override
    public void run() {
        try {
            execute();
        } catch (Throwable e) {
            if (_logger != null) {
                _logger.log(Level.SEVERE, e.getMessage(), e);
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
                            _queue.put(new FileUploadTask(_session, _logger, file, job.directory(), job.namespace(),
                                    _settings.csumCheck(), _ul));
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
    }

}
