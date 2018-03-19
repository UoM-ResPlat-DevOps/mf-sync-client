package unimelb.mf.client.sync;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import unimelb.mf.client.session.MFConnectionSettings;
import unimelb.mf.client.session.MFSession;

public class MFSyncCLI {

    public static void main(String[] args) throws Throwable {
        /*
         * load, parse & validate settings
         */
        MFConnectionSettings connectionSettings = null;
        MFSyncSettings syncSettings = null;
        MFSession session = null;
        Path directory = null; // directory from CLI args
        String parentNamespace = null; // namespace from CLI args
        try {
            /*
             * 1) log default conf file first, as it can be overridden.
             */
            connectionSettings = new MFConnectionSettings(new File(MFSync.PROPERTIES_FILE));
            syncSettings = new MFSyncSettings(new File(MFSync.PROPERTIES_FILE));
            /*
             * 2) load specified conf file, it can be overridden by other
             * arguments.
             */
            for (int i = 0; i < args.length;) {
                if (args[i].equals("--conf")) {
                    Path configFile = Paths.get(args[i + 1]);
                    if (Files.exists(configFile) && Files.isRegularFile(configFile)) {
                        connectionSettings.loadFromXmlFile(configFile.toFile());
                        syncSettings.loadFromXmlFile(configFile.toFile());
                    } else {
                        throw new IllegalArgumentException("Invalid conf argument. Config file: '" + args[i + 1]
                                + "' is not found or it is not a regular file.");
                    }
                    i += 2;
                } else {
                    i++;
                }
            }

            /*
             * 3) parse all arguments (except --conf as it has been loaded
             * previously.)
             */
            for (int i = 0; i < args.length;) {
                if (args[i].equals("--help") || args[i].equals("-h")) {
                    printUsage();
                    System.exit(0);
                } else if (args[i].equals("--mf.host")) {
                    connectionSettings.setServerHost(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--mf.port")) {
                    try {
                        connectionSettings.setServerPort(Integer.parseInt(args[i + 1]));
                    } catch (Throwable e) {
                        throw new IllegalArgumentException("Invalid mf.port: " + args[i + 1], e);
                    }
                    i += 2;
                } else if (args[i].equals("--mf.transport")) {
                    connectionSettings.setServerTransport(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--mf.auth")) {
                    String auth = args[i + 1];
                    String[] parts = auth.split(",");
                    if (parts == null || parts.length != 3) {
                        throw new IllegalArgumentException("Invalid mf.auth: " + auth);
                    }
                    connectionSettings.setUserCredentials(parts[0], parts[1], parts[2]);
                    i += 2;
                } else if (args[i].equals("--mf.token")) {
                    connectionSettings.setToken(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--mf.sid")) {
                    connectionSettings.setSessionKey(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--conf")) {
                    // skip. Because it was processed previously.
                    i += 2;
                } else if (args[i].equals("--number-of-workers")) {
                    try {
                        syncSettings.setNumberOfWorkers(Integer.parseInt(args[i + 1]));
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("Invalid --number-of-workers: " + args[i + 1], nfe);
                    }
                    i += 2;
                } else if (args[i].equals("--max-checkers")) {
                    try {
                        syncSettings.setMaxNumberOfCheckers(Integer.parseInt(args[i + 1]));
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("Invalid --max-checkers: " + args[i + 1], nfe);
                    }
                    i += 2;
                } else if (args[i].equals("--check-batch-size")) {
                    try {
                        syncSettings.setCheckBatchSize(Integer.parseInt(args[i + 1]));
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("Invalid --check-batch-size: " + args[i + 1], nfe);
                    }
                    i += 2;
                } else if (args[i].equals("--exclude-empty-folder")) {
                    syncSettings.setExcludeEmptyFolder(true);
                    i++;
                } else if (args[i].equals("--csum-check")) {
                    syncSettings.setCsumCheck(true);
                    i++;
                } else if (args[i].equals("--notification-emails")) {
                    String[] emails = args[i + 1].indexOf(',') != -1 ? args[i + 1].split(",")
                            : new String[] { args[i + 1] };
                    if (emails != null && emails.length > 0) {
                        for (String email : emails) {
                            if (email != null) {
                                email = email.trim();
                                if (!email.isEmpty()) {
                                    syncSettings.addNotificationEmailAddress(email);
                                }
                            }
                        }
                    }
                    i += 2;
                } else if (args[i].equals("--daemon")) {
                    syncSettings.setWatchDaemon(true);
                    i++;
                } else if (args[i].equals("--daemon-port")) {
                    try {
                        int daemonPort = Integer.parseInt(args[i + 1]);
                        if (daemonPort < 0 || daemonPort > 65535) {
                            throw new IllegalArgumentException(
                                    "Invalid --daemon-port value. Expects a positive integer between 1 and 65535. Found: "
                                            + args[i + 1]);
                        }
                        syncSettings.setDaemonListenerPort(daemonPort);
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException(
                                "Invalid --daemon-port value. Expects a positive integer between 1 and 65535. Found: "
                                        + args[i + 1],
                                nfe);
                    }
                    i += 2;
                } else if (args[i].equals("--daemon-scan-interval")) {
                    try {
                        int scanInterval = Integer.parseInt(args[i + 1]);
                        if (scanInterval < 0) {
                            throw new IllegalArgumentException("Invalid --daemon-scan-interval: " + args[i + 1]);
                        }
                        syncSettings.setDaemonScanInterval(scanInterval);
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException(
                                "Invalid --daemon-scan-interval value. Expects a positive integer value. Found: "
                                        + args[i + 1],
                                nfe);
                    }
                    i += 2;
                } else if (args[i].equals("--log-dir")) {
                    Path logDir = Paths.get(args[i + 1]);
                    if (Files.exists(logDir) && Files.isDirectory(logDir)) {
                        syncSettings.setLogDirectory(logDir);
                    } else {
                        throw new IllegalArgumentException("Invalid log.dir argument. Directory: '" + args[i + 1]
                                + "' is not found or it is not a directory.");
                    }
                    i += 2;
                } else {
                    if (directory == null) {
                        directory = Paths.get(args[i]);
                        if (!Files.exists(directory)) {
                            throw new IllegalArgumentException("Directory: " + directory + " does not exist.");
                        }
                        if (!Files.isDirectory(directory)) {
                            throw new IllegalArgumentException(directory.toString() + " is not a directory.");
                        }
                    } else {
                        if (parentNamespace == null) {
                            parentNamespace = args[i];
                        } else {
                            throw new IllegalArgumentException("Invalid arguments.");
                        }
                    }
                    i++;
                }
            }
            if ((directory != null && parentNamespace == null) || (directory == null && parentNamespace != null)) {
                throw new IllegalArgumentException("Invalid arguments.");
            }
            if (directory != null && parentNamespace != null) {
                syncSettings.addUploadJob(directory, parentNamespace, true);
            }
            // validate connection settings
            connectionSettings.validate();

            session = new MFSession(connectionSettings);

            // test authentication
            session.testAuthentication();

            // validate sync settings
            syncSettings.validate(session);
        } catch (Throwable e) {
            e.printStackTrace();
            if (e instanceof IllegalArgumentException) {
                System.err.println("Error: " + e.getMessage());
                printUsage();
            }
            System.exit(1);
        }
        MFSync sync = new MFSync(session, syncSettings);
        if (syncSettings.daemonEnabled()) {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

                @Override
                public void run() {
                    sync.logInfo("Shutting down gracefully...");
                    sync.stop();
                }
            }));
            new Thread(sync).start();
        } else {
            sync.run();
        }
    }

    private static void printUsage() {
        // @formatter:off
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("    "+ MFSync.APP_NAME + " [options] [<src-directory> <dst-asset-namespace>]");
        System.out.println();
        System.out.println("DESCRIPTION:");
        System.out.println("    " + MFSync.APP_NAME + " is a tool to upload files and directories to Mediaflux server. It supports also daemon mode, which runs in background to scan for local changes and upload to Mediaflux.");
        System.out.println();
        System.out.println("OPTIONS:");
        System.out.println("    --help                               Display help information.");
        System.out.println("    --mf.host <host>                     The Mediaflux server host.");
        System.out.println("    --mf.port <port>                     The Mediaflux server port.");
        System.out.println("    --mf.transport <transport>           The Mediaflux server transport, can be http, https or tcp/ip.");
        System.out.println("    --mf.auth <domain,user,password>     The Mediaflux user authentication deatils.");
        System.out.println("    --mf.token <token>                   The Mediaflux secure identity token.");
        System.out.println("    --conf <config-file>                 The configuration file. Defaults to '~/.mediaflux/mf-sync.properties'");
        System.out.println("    --number-of-workers <n>              Number of worker threads to upload the files. If not specified, defaults to 1.");
        System.out.println("    --max-checkers <n>                   Maximum number of checker threads to compare local files with Mediaflux assets. If not specified, defaults to 1."); 
        System.out.println("    --check-batch-size <n>               Batch size for comparing files with Mediaflux assets. Defaults to 100, which checks 100 files within single service request."); 
        System.out.println("    --exclude-empty-folder               Exclude empty folders. In other words, upload files only.");
        System.out.println("    --csum-check                         Validate CRC32 checksum after uploading. It will slow down the proccess.");    
        System.out.println("    --notification-emails  <a@b.org>     The (comma-separated) email addresses for notification recipients.");
        System.out.println("    --log-dir <logging-directory>        The directory for log files. If not specified, defaults to current work directory.");
        System.out.println("    --daemon                             Runs as a daemon to periodically scan the changes and upload.");
        System.out.println("    --daemon-port                        The listening port of the daemon. Defaults to 9761. It accepts connection from localhost only. It responds to 'status' and 'stop' requests. If 'status', it responds with the current application status; If 'stop', it will shutdown the daemon and exit the application. You can use netcat to send command to the daemon listener port, e.g 'echo status | nc localhost 9761' or to stop the daemon 'echo stop | nc localhost 9761'");
        System.out.println("    --daemon-scan-interval               The time interval in milliseconds between scans. Defaults to 60000 (1 minute). It only starts scanning when the daemon is idle. In other words, it skips scans if previous scan or upload has not completed. "); 
        
        System.out.println();
        System.out.println("POSITIONAL ARGUMENTS:");        
        System.out.println("    <src-directory>                      The source directory.");
        System.out.println("    <dst-asset-namespace>                The destination (parent) asset namespace.");
        System.out.println("    Note: to specify multiple 'source directory' and 'destination asset namespace' pairs, you need to add them to the configuration file as upload jobs.");
        System.out.println();
        // @formatter:on
    }

}
