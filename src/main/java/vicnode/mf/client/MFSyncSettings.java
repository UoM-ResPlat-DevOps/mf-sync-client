package vicnode.mf.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import vicnode.mf.client.util.PathUtils;

public class MFSyncSettings extends ConnectionSettings {

    public static final String PROPERTY_DIRECTORY = "directory";
    public static final String PROPERTY_NAMESPACE = "namespace";
    public static final String PROPERTY_THREADS = "threads";
    public static final String PROPERTY_WATCH = "watch";
    public static final String PROPERTY_SYNC_LOCAL_DELETION = "sync.local.deletion";
    public static final String PROPERTY_LOG_DIRECTORY = "log.dir";

    private Path _directory;
    private String _parentNamespace;
    private int _nbThreads = 1;
    private boolean _watch = false;
    private boolean _syncLocalDeletion = false;

    private Path _logDir;

    public MFSyncSettings() {
        this(null);
    }

    public MFSyncSettings(Properties properties) {
        super(properties);
    }

    public Path directory() {
        return _directory;
    }

    public void loadFromProperties(Properties properties) {
        super.loadFromProperties(properties);
        if (properties != null) {
            if (properties.containsKey(PROPERTY_DIRECTORY)) {
                _directory = Paths.get(properties.getProperty(PROPERTY_DIRECTORY));
            }

            if (properties.containsKey(PROPERTY_NAMESPACE)) {
                _parentNamespace = properties.getProperty(PROPERTY_NAMESPACE);
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
            if (properties.containsKey(PROPERTY_SYNC_LOCAL_DELETION)) {
                _syncLocalDeletion = Boolean.parseBoolean(properties.getProperty(PROPERTY_SYNC_LOCAL_DELETION));
            }
            if (properties.containsKey(PROPERTY_LOG_DIRECTORY)) {
                _logDir = Paths.get(properties.getProperty(PROPERTY_LOG_DIRECTORY));
            }
        }
    }

    public Path logDirectory() {
        return _logDir;
    }

    public String namespace() {
        return _parentNamespace;
    }

    public int numberOfThreads() {
        return _nbThreads;
    }

    public MFSyncSettings setDirectory(Path directory) {
        _directory = directory;
        return this;
    }

    public MFSyncSettings setLogDirectory(Path logDir) {
        _logDir = logDir;
        return this;
    }

    public MFSyncSettings setNamespace(String namespace) {
        _parentNamespace = namespace;
        return this;
    }

    public MFSyncSettings setNumberOfThreads(int nbThreads) {
        if (nbThreads > 1) {
            _nbThreads = nbThreads;
        }
        return this;
    }

    public MFSyncSettings setWatch(boolean watch) {
        _watch = watch;
        return this;
    }

    public MFSyncSettings setSyncLocalDeletion(boolean syncLocalDeletion) {
        _syncLocalDeletion = syncLocalDeletion;
        return this;
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
        if (_parentNamespace == null) {
            throw new IllegalArgumentException("Missing destination (parent) namespace argument.");
        }
        if (_watch) {
            Path logDir = _logDir == null ? MFSync.DEFAULT_LOG_DIR : _logDir;
            if (Files.isSameFile(logDir, _directory)) {
                throw new IllegalArgumentException("log.dir='" + logDir
                        + "' is the same as source directory. This will cause uploading log file infinitely. Change --log.dir to different location to resolve the issue.");
            }
            if (PathUtils.isOrIsDescendant(_logDir, _directory)) {
                throw new IllegalArgumentException("log.dir='" + logDir
                        + "' is contained by the source directory. This will cause uploading log file infinitely. Change --log.dir to different location to resolve the issue.");
            }
        }
    }

    public boolean watch() {
        return _watch;
    }

    public boolean syncLocalDeletion() {
        return _syncLocalDeletion;
    }

}
