package resplat.mf.client.sync;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import resplat.mf.client.session.MFConnectionSettings;
import resplat.mf.client.session.MFSession;

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
                } else if (args[i].equals("--number-of-workers")) {
                    try {
                        syncSettings.setNumberOfWorkers(Integer.parseInt(args[i + 1]));
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("Invalid number of workers: " + args[i + 1], nfe);
                    }
                    i += 2;
                } else if (args[i].equals("--daemon")) {
                    syncSettings.setWatchDaemon(true);
                    i++;
                } else if (args[i].equals("--csum-check")) {
                    syncSettings.setCsumCheck(true);
                    i++;
                } else if (args[i].equals("--exclude-empty-folder")) {
                    syncSettings.setExcludeEmptyFolder(true);
                    i++;
                } else if (args[i].equals("--log-dir")) {
                    Path logDir = Paths.get(args[i + 1]);
                    if (Files.exists(logDir) && Files.isDirectory(logDir)) {
                        syncSettings.setLogDirectory(logDir);
                    } else {
                        throw new IllegalArgumentException("Invalid log.dir argument. Directory: '" + args[i + 1]
                                + "' is not found or it is not a directory.");
                    }
                    i += 2;
                } else if (args[i].equals("--conf")) {
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
        System.out.println("    "+ MFSync.APP_NAME + " [options] [src-directory dst-asset-namespace]");
        System.out.println();
        System.out.println("DESCRIPTION:");
        System.out.println("    " + MFSync.APP_NAME + " is a tool to upload local files from the specified directory to remote Mediaflux asset namespace. It can also run as a daemon to monitor the local changes in the directory and synchronize to the asset namespace.");
        System.out.println();
        System.out.println("OPTIONS:");
        System.out.println("    --help                               Display help information.");
        System.out.println("    --mf.host <host>                     The Mediaflux server host.");
        System.out.println("    --mf.port <port>                     The Mediaflux server port.");
        System.out.println("    --mf.transport <transport>           The Mediaflux server transport, can be http, https or tcp/ip.");
        System.out.println("    --mf.auth <domain,user,password>     The Mediaflux user authentication deatils.");
        System.out.println("    --mf.token <token>                   The Mediaflux secure identity token.");
        System.out.println("    --mf.sid <sid>                       The Mediaflux session id.");
        System.out.println("    --daemon                             Start a daemon to periodically scan the changes in the specified directory.");
        System.out.println("    --csum-check                         Validate CRC32 checksum are upload. It will slow down the upload process.");
        System.out.println("    --exclude-empty-folder               Exclude empty folders.");
        System.out.println("    --number-of-workers <n>              Number of worker threads to upload the files. Defaults to 1.");
        System.out.println("    --log-dir <logging-directory>        The directory to save the logs. Defaults to current work directory.");
        System.out.println("    --conf <config-file>                 The configuration file. Defaults to ~/.mediaflux/mf-sync.properties Note: settings in the configuration file can be overridden by the command arguments.");
        System.out.println();
        System.out.println("POSITIONAL ARGUMENTS:");        
        System.out.println("    <src-directory>                      The local sourcedirectory to upload/synchronize from.");
        System.out.println("    <dst-asset-namespace>                The remote (parent) destination asset namespace to upload/synchronize to.");
        System.out.println();
        // @formatter:on
    }

}
