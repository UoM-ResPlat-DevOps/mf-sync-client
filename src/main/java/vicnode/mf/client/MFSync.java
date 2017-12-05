package vicnode.mf.client;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import arc.xml.XmlStringWriter;
import vicnode.mf.client.file.Filter;
import vicnode.mf.client.task.sync.AssetSyncTaskProducer;
import vicnode.mf.client.task.sync.FileSyncTaskProducer;
import vicnode.mf.client.task.sync.FileWatchTaskProducer;
import vicnode.mf.client.task.sync.PoisonTask;
import vicnode.mf.client.task.sync.SyncTask;
import vicnode.mf.client.task.sync.TaskConsumer;
import vicnode.mf.client.util.LoggingUtils;
import vicnode.mf.client.util.PathUtils;
import vicnode.mf.client.util.ThrowableUtils;

public class MFSync implements Runnable {

    public static final String APP_NAME = "mf-sync";

    public static final String PROPERTIES_FILE = new StringBuilder()
            .append(System.getProperty("user.home").replace('\\', '/')).append("/.mediaflux/mf-sync.properties")
            .toString();

    public static final int MB = 1000000;
    public static final int LOG_FILE_SIZE_LIMIT = 100 * MB;
    public static final int LOG_FILE_COUNT = 2;
    public static final String LOG_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final boolean IS_WINDOWS = System.getProperty("os.name", "generic").toLowerCase()
            .indexOf("windows") >= 0;

    public static final Path DEFAULT_LOG_DIR = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

    private MFSession _session;
    private Path _directory;
    private String _namespace;

    private Path _logDir;
    private Logger _logger;

    private BlockingQueue<SyncTask> _queue;

    private ExecutorService _producerThreadPool;

    private int _nbConsumers;
    private ExecutorService _consumerThreadPool;
    private List<TaskConsumer> _consumers;

    private boolean _watch;
    private boolean _syncLocalDeletion;

    public MFSync(MFSyncSettings settings) {
        this(new MFSession(settings.setApp(APP_NAME)), settings.directory(), settings.namespace(),
                settings.numberOfThreads(), settings.watch(), settings.syncLocalDeletion(), settings.logDirectory());
    }

    public MFSync(MFSession session, Path directory, String namespace, int nbConsumers, boolean watch,
            boolean syncLocalDeletion, Path logDir) {
        _session = session;
        _directory = directory;
        _namespace = PathUtils.join(namespace, directory.getFileName().toString());

        _logDir = logDir == null ? DEFAULT_LOG_DIR : logDir.toAbsolutePath();
        _logger = createLogger(_logDir, APP_NAME);

        _queue = new LinkedBlockingQueue<SyncTask>();

        _producerThreadPool = Executors.newSingleThreadExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Task Producer");
            }
        });

        _nbConsumers = nbConsumers;
        _consumerThreadPool = Executors.newFixedThreadPool(nbConsumers, new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Task Consumer");
            }
        });

        _watch = watch;
        _syncLocalDeletion = syncLocalDeletion;

    }

    @Override
    public void run() {
        try {
            /*
             * run server.ping periodically to keep the session alive
             */
            _session.startPingServerPeriodically(60000);

            /*
             * check if directory exists
             */
            if (!Files.exists(_directory)) {
                throw new IllegalArgumentException("Directory: '" + _directory + "' does not exist.");
            }

            /*
             * check if namespace exists
             */
            XmlStringWriter w = new XmlStringWriter();
            w.add("namespace", _namespace);
            boolean namespaceExists = _session.execute("asset.namespace.exists", w.document(), null, null, null)
                    .booleanValue("exists");
            if (!namespaceExists) {
                throw new IllegalArgumentException("Asset namespace: '" + _namespace + "' does not exist.");
            }

            LoggingUtils.logInfo(_logger,
                    "Syncing from directory: '" + _directory + "' to asset namespace: '" + _namespace + "'");

            Filter logFileFilter = new Filter() {

                @Override
                public boolean acceptFile(Path file, BasicFileAttributes attrs) {
                    String path = file.toAbsolutePath().toString();
                    String logFilePrefix = logFilePrefix();
                    boolean exclude = path.startsWith(logFilePrefix + ".")
                            && (path.endsWith(".log") || path.endsWith(".log.lck"));
                    if (exclude) {
                        // skip log file.
                        return false;
                    } else {
                        return true;
                    }
                }

                @Override
                public boolean acceptDirectory(Path dir, BasicFileAttributes attrs) {
                    return true;
                }
            };

            /*
             * Run FileSyncTaskProducer: go through the files in the local
             * directory, and upload them to the remote asset namespace.
             */
            _producerThreadPool.submit(new FileSyncTaskProducer(_session, _logger, _directory, _namespace, true, _queue)
                    .setFilter(logFileFilter));

            /*
             * Run AssetSyncTaskProducer: go through the assets in the remote
             * asset namespace, and delete the assets do not exist locally
             */
            if (_syncLocalDeletion) {
                _producerThreadPool
                        .submit(new AssetSyncTaskProducer(_session, _logger, _directory, _namespace, _queue));
            }

            /*
             * Start consumer threads.
             */
            _consumers = new ArrayList<TaskConsumer>(_nbConsumers);
            for (int i = 0; i < _nbConsumers; i++) {
                TaskConsumer consumer = new TaskConsumer(_queue, _logger);
                _consumers.add(consumer);
                _consumerThreadPool.submit(consumer);
            }

            /*
             * Watch the changes in the local directory, upload files to remote
             * asset namespace...
             */
            if (_watch) {
                _producerThreadPool.submit(
                        new FileWatchTaskProducer(_session, _logger, _directory, _namespace, _queue, _syncLocalDeletion)
                                .setFilter(logFileFilter));
            } else {
                _producerThreadPool.shutdown();
                _producerThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
                for (int i = 0; i < _nbConsumers; i++) {
                    _queue.put(new PoisonTask());
                }
                _consumerThreadPool.shutdown();
                _consumerThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
                _session.stopPingServerPeriodically();
            }

        } catch (Throwable e) {
            LoggingUtils.logError(_logger, e);
        }
    }

    public void start() {
        run();
    }

    public void stop() {
        if (_consumerThreadPool != null && !_consumerThreadPool.isTerminated()) {
            _consumerThreadPool.shutdownNow();
        }
        if (_producerThreadPool != null && !_producerThreadPool.isTerminated()) {
            _producerThreadPool.shutdownNow();
        }
        _session.stopPingServerPeriodically();
    }

    protected String logFilePrefix() {
        return Paths.get(_logDir.toString(), APP_NAME).toString();
    }

    public void log(Level level, String message, Throwable thrown) {
        LoggingUtils.log(_logger, level, message, thrown);
    }

    public void logInfo(String message) {
        log(Level.INFO, message, null);
    }

    public void logWarning(String message) {
        log(Level.WARNING, message, null);
    }

    public void logError(String message, Throwable thrown) {
        log(Level.SEVERE, message, thrown);
    }

    public void logError(Throwable thrown) {
        log(Level.SEVERE, thrown.getMessage(), thrown);
    }

    public void logError(String message) {
        log(Level.SEVERE, message, null);
    }

    static Logger createLogger(Path dir, String logName) {

        try {
            Logger logger = Logger.getLogger(logName);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);

            /*
             * add file handler
             */
            String logFileNamePattern = dir.toString() + File.separatorChar + logName + "." + "%g.log";
            FileHandler fileHandler = new FileHandler(logFileNamePattern, LOG_FILE_SIZE_LIMIT, LOG_FILE_COUNT, true);
            fileHandler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    StringBuilder sb = new StringBuilder();
                    // date & time
                    sb.append(new SimpleDateFormat(LOG_DATE_FORMAT).format(new Date(record.getMillis()))).append(" ");
                    // thread
                    sb.append("[thread ").append(record.getThreadID()).append("] ");
                    sb.append(String.format("%7s", record.getLevel().getName().toUpperCase())).append(" ");
                    sb.append(record.getMessage());
                    if (IS_WINDOWS) {
                        sb.append("\r");
                    }
                    sb.append("\n");
                    Throwable error = record.getThrown();
                    if (error != null) {
                        sb.append(ThrowableUtils.getStackTrace(error));
                        if (IS_WINDOWS) {
                            sb.append("\r");
                        }
                        sb.append("\n");
                    }
                    return sb.toString();
                }
            });
            fileHandler.setLevel(Level.ALL);
            logger.addHandler(fileHandler);
            return logger;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create logger: " + t.getMessage(), t);
        }
    }

}
