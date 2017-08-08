package vicnode.mf.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class MFSyncSettings extends ConnectionSettings {

    public static final String PROPERTY_DIRECTORY = "directory";
    public static final String PROPERTY_NAMESPACE = "namespace";
    public static final String PROPERTY_THREADS = "threads";
    public static final String PROPERTY_WATCH = "watch";
    public static final String PROPERTY_LOG_DIRECTORY = "log.dir";

    private Path _directory;
    private String _namespace;
    private int _nbThreads = 1;
    private boolean _watch = false;

    private Path _logDir;

    public MFSyncSettings(Properties properties) {
        super(properties);
    }

    public MFSyncSettings() {
        this(null);
    }

    public Path directory() {
        return _directory;
    }

    public void setDirectory(Path directory) {
        _directory = directory;
    }

    public String namespace() {
        return _namespace;
    }

    public void setNamespace(String namespace) {
        _namespace = namespace;
    }

    public int numberOfThreads() {
        return _nbThreads;
    }

    public void setNumberOfThreads(int nbThreads) {
        if (nbThreads > 1) {
            _nbThreads = nbThreads;
        }
    }

    public boolean watch() {
        return _watch;
    }

    public void setWatch(boolean watch) {
        _watch = watch;
    }

    public Path logDirectory() {
        return _logDir;
    }

    public void setLogDirectory(Path logDir) {
        _logDir = logDir;
    }

    public void loadFromProperties(Properties properties) {
        super.loadFromProperties(properties);
        if (properties != null) {
            if (properties.containsKey(PROPERTY_DIRECTORY)) {
                _directory = Paths.get(properties.getProperty(PROPERTY_DIRECTORY));
            }
            if (properties.containsKey(PROPERTY_NAMESPACE)) {
                _namespace = properties.getProperty(PROPERTY_NAMESPACE);
            }
            if (properties.containsKey(PROPERTY_THREADS)) {
                try {
                    _nbThreads = Integer.parseInt(properties.getProperty(PROPERTY_THREADS));
                } catch (NumberFormatException nfe) {
                    _nbThreads = 1;
                }
            }
            if (properties.containsKey(PROPERTY_WATCH)) {
                _watch = Boolean.parseBoolean(properties.getProperty(PROPERTY_WATCH));
            }
            if (properties.containsKey(PROPERTY_LOG_DIRECTORY)) {
                _logDir = Paths.get(properties.getProperty(PROPERTY_LOG_DIRECTORY));
            }
        }
    }

    public void validate() throws Throwable {
        super.validate();
        if (_directory == null) {
            throw new IllegalArgumentException("Missing directory argument.");
        } else {
            if (!Files.exists(_directory)) {
                throw new IllegalArgumentException(
                        "Invalid directory argument. Directory: '" + _directory + "' does not exist.");
            }
        }
        if (_namespace == null) {
            throw new IllegalArgumentException("Missing namespace argument.");
        }
    }

}
