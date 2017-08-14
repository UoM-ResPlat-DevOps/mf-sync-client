package vicnode.mf.client.task.sync;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

import arc.utils.CanAbort;
import arc.xml.XmlDoc;
import arc.xml.XmlStringWriter;
import vicnode.mf.client.MFSession;
import vicnode.mf.client.file.Filter;
import vicnode.mf.client.util.HasAbortableOperation;
import vicnode.mf.client.util.LoggingUtils;
import vicnode.mf.client.util.PathUtils;

/**
 * 
 * @author wliu5
 * 
 *         https://docs.oracle.com/javase/tutorial/displayCode.html?code=https://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java
 *
 */
public class FileWatchTaskProducer implements Runnable, HasAbortableOperation {

    private MFSession _session;
    private Logger _logger;
    private Path _rootDirectory;
    private String _rootNamespace;
    private BlockingQueue<SyncTask> _queue;

    private WatchService _watcher;
    private Map<WatchKey, Path> _watchKeys;

    private CanAbort _ca;

    private Filter _filter;

    public FileWatchTaskProducer(MFSession session, Logger logger, Path rootDirectory, String rootNamespace,
            BlockingQueue<SyncTask> queue) throws IOException {
        _session = session;
        _logger = logger;
        _rootDirectory = rootDirectory;
        _rootNamespace = rootNamespace;
        _queue = queue;

        _watcher = FileSystems.getDefault().newWatchService();
        _watchKeys = new HashMap<WatchKey, Path>();
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(_watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        Path prev = _watchKeys.get(key);
        if (prev == null) {
            LoggingUtils.logInfo(_logger, String.format("Started watching directory: %s", dir));
        } else {
            if (!dir.equals(prev)) {
                LoggingUtils.logInfo(_logger, String.format("Updated watching directory: %s -> %s", prev, dir));
            }
        }
        _watchKeys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(Path rootDir) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void processEvents(WatchKey key) throws Throwable {
        Path dir = _watchKeys.get(key);
        if (dir == null) {
            LoggingUtils.logError(_logger, "WatchKey is not recognized!");
            return;
        }
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == OVERFLOW) {
                LoggingUtils.logWarning(_logger, "Encountered OVERFLOW event.");
                continue;
            }

            // Context for directory entry event is the file name of
            // entry
            @SuppressWarnings("unchecked")
            WatchEvent<Path> ev = (WatchEvent<Path>) event;
            Path name = ev.context();
            Path child = dir.resolve(name);

            boolean isDirectory = Files.isDirectory(child, NOFOLLOW_LINKS);
            boolean isRegularFile = Files.isRegularFile(child, NOFOLLOW_LINKS);

            // log event
            if (_filter != null && isRegularFile && !_filter.acceptFile(child)) {
                // DO not log it.
            } else {
                LoggingUtils.logInfo(_logger, String.format("Processing event: %s: %s", ev.kind().name(), child));
            }

            if (kind == ENTRY_DELETE) {
                SimpleEntry<Boolean, Boolean> exists = exists(_session,
                        PathUtils.join(_rootNamespace, SyncTask.relativePath(_rootDirectory, child)));
                boolean namespaceExists = exists.getKey();
                if (namespaceExists) {
                    // destroy all assets in the corresponding namespace
                    final String namespace = PathUtils.join(_rootNamespace,
                            SyncTask.relativePath(_rootDirectory, child));
                    LoggingUtils.logInfo(_logger, "Destroying namespace: '" + namespace + "'");
                    softDestroyAllAssets(_session, namespace);
                }
                boolean assetExists = exists.getValue();
                if (assetExists) {
                    _queue.put(new AssetDestroyTask(_session, _logger, child, _rootDirectory, _rootNamespace));
                }
            } else {
                if (!isDirectory && !isRegularFile) {
                    LoggingUtils.logInfo(_logger, "Skipped: " + child + ". Not a directory or regular file.");
                    continue;
                }
                if (kind == ENTRY_CREATE) {
                    if (isDirectory) {
                        // if directory is created, and watching recursively,
                        // then register it and its sub-directories
                        try {
                            registerAll(child);
                        } catch (IOException ioe) {
                            LoggingUtils.logError(_logger, "Failed to register: " + child, ioe);
                        }
                        // upload the directory (It may be empty if the event
                        // was triggered by mkdir; Otherwise it may contains
                        // files if the event was triggered by mv)
                        if (_filter == null || _filter.acceptDirectory(child)) {
                            new FileSyncTaskProducer(_session, _logger, child,
                                    PathUtils.join(_rootNamespace, SyncTask.relativePath(_rootDirectory, child)),
                                    _queue).execute();
                        }
                    } else if (isRegularFile) {
                        if (_filter == null || _filter.acceptFile(child)) {
                            _queue.put(new FileUploadTask(_session, _logger, child, _rootDirectory, _rootNamespace));
                        }
                    }
                } else if (kind == ENTRY_MODIFY) {
                    if (isDirectory) {
                        // TODO
                    } else if (isRegularFile) {
                        if (_filter == null || _filter.acceptFile(child)) {
                            _queue.put(new FileUploadTask(_session, _logger, child, _rootDirectory, _rootNamespace));
                        }
                    }
                }
            }
        }
    }

    public FileWatchTaskProducer setFilter(Filter filter) {
        _filter = filter;
        return this;
    }

    @Override
    public void run() {
        try {
            registerAll(_rootDirectory);
            while (!Thread.interrupted()) {
                // wait for key to be signaled
                WatchKey key = _watcher.take();
                processEvents(key);

                // reset key and remove from set if directory no longer
                // accessible
                boolean valid = key.reset();
                if (!valid) {
                    _watchKeys.remove(key);
                }

                // all directories are inaccessible?
                if (_watchKeys.isEmpty()) {
                    break;
                }
            }
        } catch (Throwable e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                LoggingUtils.logInfo(_logger, "Interrupted '" + Thread.currentThread().getName() + "' thread(id="
                        + Thread.currentThread().getId() + ").");
            } else {
                LoggingUtils.logError(_logger, e);
            }
        }
    }

    private SimpleEntry<Boolean, Boolean> exists(MFSession session, String path) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.push("service", new String[] { "name", "asset.namespace.exists" });
        w.add("namespace", path);
        w.pop();
        w.push("service", new String[] { "name", "asset.exists" });
        w.add("id", "path=" + path);
        w.pop();
        XmlDoc.Element re = session.execute("service.execute", w.document(), null, null, this);
        return new SimpleEntry<Boolean, Boolean>(
                re.booleanValue("reply[@service='asset.namespace.exists']/response/exists"),
                re.booleanValue("reply[@service='asset.exists']/response/exists"));
    }

    private void softDestroyAllAssets(MFSession session, String namespace) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("where", "namespace>='" + namespace + "'");
        w.add("action", "pipe");
        w.add("service", new String[] { "name", "asset.soft.destroy" });
        _session.execute("asset.query", w.document(), null, null, this);
    }

    @Override
    public void setAbortableOperation(CanAbort ca) {
        _ca = ca;
    }

    @Override
    public CanAbort abortableOperation() {
        return _ca;
    }

}
