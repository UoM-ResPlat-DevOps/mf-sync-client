package resplat.mf.client.sync;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MFSyncDaemonClientCLI {

    public static final String PROG = "mf-sync-daemon-client";

    public static void main(String[] args) throws Throwable {

        int port = MFSync.DEFAULT_DAEMON_PORT;
        String command = null;
        try {
            if (Files.exists(Paths.get(MFSync.PROPERTIES_FILE))) {
                MFSyncSettings settings = new MFSyncSettings(new File(MFSync.PROPERTIES_FILE));
                port = settings.daemonPort();
            }
            for (int i = 0; i < args.length;) {
                if ("-h".equalsIgnoreCase(args[i]) || "--help".equalsIgnoreCase(args[i])) {
                    printUsage();
                    System.exit(0);
                } else if ("-p".equalsIgnoreCase(args[i]) || "--port".equalsIgnoreCase(args[i])) {
                    port = Integer.parseInt(args[i + 1]);
                    i += 2;
                } else {
                    if (command == null) {
                        if ("stop".equalsIgnoreCase(args[i]) || "status".equalsIgnoreCase(args[i])) {
                            command = args[i];
                            i++;
                            continue;
                        }
                    }
                    throw new IllegalArgumentException("Invalid arguments.");
                }
            }
            if (command == null) {
                throw new IllegalArgumentException("Invalid arguments. Missing command: stop or status.");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            if (e instanceof IllegalArgumentException) {
                printUsage();
            }
            System.exit(1);
        }

        Socket socket = new Socket(InetAddress.getByName(null), port);
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintStream out = new PrintStream(socket.getOutputStream(), true);
            out.println(command);
            out.flush();
            String response = null;
            do {
                response = in.readLine();
                if (response != null) {
                    System.out.println(response);
                }
            } while (response != null);
        } finally {
            socket.close();
        }
    }

    public static void printUsage() {
        System.out.println();
        System.out.println(String.format("Usage: %s [-p port] <stop|status>", PROG));
        System.out.println();
        System.out.println("Options:");
        System.out.println("    -h | --help               Prints help.");
        System.out.println(
                "    -p | --port <port>        The daemon port. If not specified, use the value from ~/.mediaflux/mf-sync-properties.xml. Defaults to 9761.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println(String.format("    %s stop           Stops the mf-sync daemon.", PROG));
        System.out.println(String.format("    %s status         Prints the status of mf-sync daemon.", PROG));
    }

}
