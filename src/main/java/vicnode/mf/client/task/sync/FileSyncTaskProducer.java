package vicnode.mf.client.task.sync;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import arc.mf.client.ServerClient;
import vicnode.mf.client.util.LoggingUtils;

public class FileSyncTaskProducer implements Runnable {

    private ServerClient.Connection _cxn;
    private Logger _logger;
    private BlockingQueue<SyncTask> _queue;

    private Path _rootDirectory;
    private String _rootNamespace;

    public FileSyncTaskProducer(ServerClient.Connection cxn, Logger logger, Path rootDirectory, String rootNamespace,
            BlockingQueue<SyncTask> queue) {
        _cxn = cxn;
        _logger = logger;
        _queue = queue;
        _rootDirectory = rootDirectory;
        _rootNamespace = rootNamespace;
    }

    @Override
    public void run() {
        try {
            execute();
        } catch (Throwable e) {
            LoggingUtils.log(_logger, Level.SEVERE, e.getMessage(), e);
        }
    }

    protected void execute() throws Throwable {
        try {
            Files.walkFileTree(_rootDirectory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        _queue.put(new FileUploadTask(_cxn.duplicate(true), _logger, file, _rootDirectory,
                                _rootNamespace));
                    } catch (Throwable e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                            return FileVisitResult.TERMINATE;
                        }
                        LoggingUtils.log(_logger, Level.SEVERE, e.getMessage(), e);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException ioe) {
                    LoggingUtils.log(_logger, Level.SEVERE, "Failed to access file: " + file, ioe);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException ioe) {
                    if (ioe != null) {
                        LoggingUtils.log(_logger, Level.SEVERE, ioe.getMessage(), ioe);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } finally {
            _cxn.close();
        }
    }

}
