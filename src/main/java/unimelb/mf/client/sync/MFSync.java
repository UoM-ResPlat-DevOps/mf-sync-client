package unimelb.mf.client.sync;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import arc.xml.XmlStringWriter;
import unimelb.mf.client.file.Filter;
import unimelb.mf.client.session.MFSession;
import unimelb.mf.client.sync.task.FileSyncTaskProducer;
import unimelb.mf.client.sync.task.FileUploadListener;
import unimelb.mf.client.sync.task.TaskConsumer;
import unimelb.mf.client.task.Loggable;
import unimelb.mf.client.task.Task;
import unimelb.mf.client.util.LoggingUtils;

public class MFSync implements Runnable, Loggable, FileUploadListener {

    public static final String APP_NAME = "mf-sync";

    public static final String PROPERTIES_FILE = new StringBuilder()
            .append(System.getProperty("user.home").replace('\\', '/')).append("/.mediaflux/mf-sync-properties.xml")
            .toString();

    public static final Path DEFAULT_LOG_DIR = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

    public static final int MAX_FAILED_UPLOADS = 100;

    public static final int MAX_ACTIVITIES = 100;

    public static final int DEFAULT_DAEMON_LISTENER_PORT = 9761;

    public static final int DEFAULT_DAEMON_SCAN_INTERVAL = 60000; // milliseconds
    
    public static final int DEFAULT_CHECK_BATCH_SIZE = 100;
    
    public static final int DEFAULT_MAX_NUMBER_OF_CHECKERS = 4;

    private MFSession _session;

    private MFSyncSettings _settings;

    private Logger _logger;

    private BlockingQueue<Task> _queue;

    private ExecutorService _producerThreadPool;

    private ExecutorService _consumerThreadPool;

    private List<TaskConsumer> _consumers;

    private AtomicInteger _nbUploaded = new AtomicInteger();
    private AtomicInteger _nbFailed = new AtomicInteger();
    private AtomicInteger _nbSkipped = new AtomicInteger();
    private AtomicLong _bytesUploaded = new AtomicLong();
    private List<Path> _failedFiles = Collections.synchronizedList(new ArrayList<Path>());
    private Map<Long, Path> _activities = Collections.synchronizedMap(new LinkedHashMap<Long, Path>() {
        private static final long serialVersionUID = -8971798382879040126L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Path> eldest) {
            return this.size() > MAX_ACTIVITIES;
        }
    });

    private long _startTime;

    private Thread _daemonListenerThread;

    private ServerSocket _listenerSocket;

    public MFSync(MFSession session, MFSyncSettings settings) {
        _session = session;
        _settings = settings;

        try {
            _logger = LoggingUtils.createFileAndConsoleLogger(_settings.logDirectory(), APP_NAME);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create logger: " + e.getMessage(), e);
        }

        _queue = new LinkedBlockingQueue<Task>();

        _producerThreadPool = Executors.newSingleThreadExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Task Producer");
            }
        });

        _consumerThreadPool = Executors.newFixedThreadPool(_settings.numberOfWorkers(), new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Task Consumer");
            }
        });

    }

    @Override
    public void run() {
        try {
            _startTime = System.currentTimeMillis();

            /*
             * run server.ping periodically to keep the session alive
             */
            _session.startPingServerPeriodically(60000);

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
            _producerThreadPool.submit(
                    new FileSyncTaskProducer(_session, _logger, _settings, this, _queue).setFilter(logFileFilter));
// @formatter:off
//            /*
//             * Run AssetSyncTaskProducer: go through the assets in the remote
//             * asset namespace, and delete the assets do not exist locally
//             */
//            if (_syncLocalDeletion) {
//                _producerThreadPool
//                        .submit(new AssetSyncTaskProducer(_session, _logger, _directory, _namespace, _queue));
//            }
// @formatter:on

            /*
             * Start consumer threads.
             */
            _consumers = new ArrayList<TaskConsumer>(_settings.numberOfWorkers());
            for (int i = 0; i < _settings.numberOfWorkers(); i++) {
                TaskConsumer consumer = new TaskConsumer(_queue, _logger);
                _consumers.add(consumer);
                _consumerThreadPool.submit(consumer);
            }

            /*
             * Watch the changes in the local directory, upload files to remote
             * asset namespace...
             */

            // starts listener
            startDaemonListener();

            if (_settings.daemonEnabled()) {
                // _producerThreadPool.submit(
                // new FileWatchTaskProducer(_session, _logger, _settings, this,
                // _queue).setFilter(logFileFilter));
            } else {

                _producerThreadPool.shutdown();
                _producerThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
//                int nbWorkers = _settings.numberOfWorkers();
//                for (int i = 0; i < nbWorkers; i++) {
//                    _queue.put(new PoisonTask());
//                }
                _consumerThreadPool.shutdown();
                _consumerThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
                printSummary(System.out);
                mailSummary();
                _session.stopPingServerPeriodically();
                _session.discard();
                stopDaemonListener();
            }

        } catch (Throwable e) {
            logError(e);
        }
    }

    private void mailSummary() throws Throwable {
        if (_settings.hasNotificationEmailAddresses()) {
            XmlStringWriter w = new XmlStringWriter();
            w.add("from", MFSync.APP_NAME + "@" + _session.serverHost());
            Collection<String> emailAddresses = _settings.notificationEmailAddresses();
            for (String emailAddress : emailAddresses) {
                w.add("to", emailAddress);
            }
            w.add("subject", MFSync.APP_NAME.toUpperCase() + " Summary");
            w.add("body", getSummary());
            _session.execute("mail.send", w.document(), null, null);
        }
    }

    private String getSummary() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        try {
            printSummary(ps);
            return baos.toString();
        } finally {
            ps.close();
            baos.close();
        }
    }

    private void printSummary(PrintStream ps) {
        _settings.print(ps);

        ps.println();
        ps.println("Summary:");
        int uploaded = _nbUploaded.get();
        int failed = _nbFailed.get();
        int skipped = _nbSkipped.get();
        int total = uploaded + failed + skipped;
        long totalBytes = _bytesUploaded.get();

        ps.println(String.format("    number-of-uploaded-files: %16d", uploaded));
        ps.println(String.format("      number-of-failed-files: %16d", failed));
        ps.println(String.format("     number-of-skipped-files: %16d", skipped));
        ps.println(String.format("       total-processed-files: %16d", total));
        ps.println(String.format("        total-uploaded-bytes: %16d bytes", totalBytes));

        if (!_settings.daemonEnabled()) {
            double speed = totalBytes == 0 ? 0.0
                    : ((double) totalBytes / 1000000.0) / ((System.currentTimeMillis() - _startTime) / 1000.0);
            ps.println(String.format("                upload-speed: %16.3f MB/s", speed));
        }
        ps.println();
        synchronized (_activities) {
            if (!_activities.isEmpty()) {
                ps.println("    Recent activities:");
                _activities.keySet().stream().sorted((lv1, lv2) -> {
                    return Long.compare(lv2, lv1);
                }).forEach(time -> {
                    Path file = _activities.get(time);
                    ps.println(String.format("        %s: uploading '%s'",
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(time)), file.toString()));
                });
            }
        }
        ps.println();
        synchronized (_failedFiles) {
            if (!_failedFiles.isEmpty()) {
                ps.println("    Failed files:");
                for (Path f : _failedFiles) {
                    ps.println(String.format("        %s", f.toString()));
                }
                if (_failedFiles.size() < failed) {
                    ps.println("        ... ... ...");
                }
            }
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
        _session.discard();
        if (_settings.daemonEnabled() && _daemonListenerThread != null && !_daemonListenerThread.isInterrupted()) {
            stopDaemonListener();
        }
    }

    public void startDaemonListener() {
        if (_daemonListenerThread == null) {
            _daemonListenerThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        _listenerSocket = new ServerSocket(_settings.daemonListenerPort(), 0, InetAddress.getByName(null));
                        try {
                            outerloop: while (!Thread.interrupted() && !_listenerSocket.isClosed()) {
                                Socket client = _listenerSocket.accept();
                                try {
                                    BufferedReader in = new BufferedReader(
                                            new InputStreamReader(client.getInputStream()));
                                    while (!Thread.interrupted()) {
                                        String cmd = in.readLine();
                                        if ("stop".equalsIgnoreCase(cmd)) {
                                            stop();
                                            break outerloop;
                                        } else if ("status".equalsIgnoreCase(cmd)) {
                                            printSummary(new PrintStream(client.getOutputStream(), true));
                                            break;
                                        } else {
                                            break;
                                        }
                                    }
                                } finally {
                                    client.close();
                                }
                            }
                        } catch (SocketException se) {
                            logInfo("Listening socket closed!");
                        } finally {
                            _listenerSocket.close();
                        }
                    } catch (Throwable e) {
                        logError(e);
                    }
                }
            }, "mf-sync daemon listener");
            _daemonListenerThread.start();
        }
    }

    public void stopDaemonListener() {
        if (_daemonListenerThread != null) {
            try {
                _listenerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                _daemonListenerThread.interrupt();
            }
        }
    }

    protected String logFilePrefix() {
        return Paths.get(_settings.logDirectory().toString(), APP_NAME).toString();
    }

    public void log(Level level, String message, Throwable thrown) {
        _logger.log(level, message, thrown);
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

    @Override
    public void fileUploadCompleted(Path file, String assetId) {
        _nbUploaded.incrementAndGet();
    }

    @Override
    public void fileUploadFailed(Path file) {
        _nbFailed.incrementAndGet();
        synchronized (_failedFiles) {
            if (_failedFiles.size() < MAX_FAILED_UPLOADS) {
                _failedFiles.add(file);
            }
        }
    }

    @Override
    public void fileUploadSkipped(Path file) {
        _nbSkipped.incrementAndGet();
    }

    @Override
    public void fileUploadProgressed(long bytesUploaded) {
        _bytesUploaded.addAndGet(bytesUploaded);
    }

    @Override
    public void fileUploadStarted(Path file) {
        synchronized (_activities) {
            _activities.put(System.currentTimeMillis(), file);
        }
    }

}
