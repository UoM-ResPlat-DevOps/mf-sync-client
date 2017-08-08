package vicnode.mf.client;

import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import arc.mf.client.RemoteServer;
import arc.mf.client.ServerClient;
import vicnode.mf.client.task.sync.AssetSyncTaskProducer;
import vicnode.mf.client.task.sync.FileSyncTaskProducer;
import vicnode.mf.client.task.sync.FileWatchTaskProducer;
import vicnode.mf.client.task.sync.SyncTask;
import vicnode.mf.client.task.sync.TaskConsumer;
import vicnode.mf.client.util.AssetNamespaceUtils;
import vicnode.mf.client.util.LoggingUtils;
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

    public static final String DEFAULT_LOG_DIR = System.getProperty("user.dir");

    private ConnectionSettings _connectionSettings;
    private Path _rootDirectory;
    private String _rootNamespace;

    private Logger _logger;

    private BlockingQueue<SyncTask> _queue;

    private ExecutorService _producerThreadPool;

    private int _nbConsumers;
    private ExecutorService _consumerThreadPool;
    private List<TaskConsumer> _consumers;

    private RemoteServer _rs;
    private ServerClient.Connection _cxn;

    private boolean _watch;

    public MFSync(ConnectionSettings connectionSettings, Path rootDirectory, String rootNamespace, int nbConsumers,
            boolean watch, Path logDir) {
        _connectionSettings = connectionSettings;
        _connectionSettings.setApp(APP_NAME);
        _rootDirectory = rootDirectory;
        _rootNamespace = rootNamespace;

        _logger = createLogger(logDir == null ? DEFAULT_LOG_DIR : logDir.toString(), APP_NAME);

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

    }

    private void connect() throws Throwable {
        if (_rs == null) {
            _rs = new RemoteServer(_connectionSettings.serverHost(), _connectionSettings.serverPort(),
                    _connectionSettings.useHttp(), _connectionSettings.encrypt());
        }
        if (_cxn == null) {
            _cxn = _rs.open();
        }
        if (!_cxn.hasSession()) {
            _cxn.connect(_connectionSettings.authenticationDetails());
        }
    }

    @Override
    public void run() {
        try {
            /*
             * Connect to Mediaflux server
             */
            connect();

            /*
             * check if the remote namespace exists.
             */
            if (!AssetNamespaceUtils.namespaceExists(_cxn, _rootNamespace)) {
                throw new IllegalArgumentException("Asset namespace: '" + _rootNamespace + "' does not exist.");
            }

            LoggingUtils.logInfo(_logger,
                    "Syncing from directory: '" + _rootDirectory + "' to asset namespace: '" + _rootNamespace + "'");

            /*
             * Run FileSyncTaskProducer: go through the files in the local
             * directory, and upload them to the remote asset namespace.
             */
            _producerThreadPool.submit(new FileSyncTaskProducer(_cxn, _logger, _rootDirectory, _rootNamespace, _queue));

            /*
             * Run AssetSyncTaskProducer: go through the assets in the remote
             * asset namespace, and delete the assets do not exist locally
             */
            _producerThreadPool
                    .submit(new AssetSyncTaskProducer(_cxn, _logger, _rootDirectory, _rootNamespace, _queue));

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
                _producerThreadPool
                        .submit(new FileWatchTaskProducer(_cxn, _logger, _rootDirectory, _rootNamespace, _queue));
            }

        } catch (Throwable e) {
            e.printStackTrace(System.err);
            if (_cxn != null) {
                _cxn.closeNe();
            }
        }
    }

    public void start() {
        run();
    }

    public void stop() {
        if (_consumerThreadPool != null && _consumerThreadPool.isShutdown()) {
            _consumerThreadPool.shutdownNow();
        }
        if (_producerThreadPool != null && _producerThreadPool.isShutdown()) {
            _producerThreadPool.shutdownNow();
        }
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

    static Logger createLogger(String dir, String logName) {

        try {
            Logger logger = Logger.getLogger(logName);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);

            /*
             * add file handler
             */
            String logFileNamePattern = dir + File.separatorChar + logName + "." + "%g.log";
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
