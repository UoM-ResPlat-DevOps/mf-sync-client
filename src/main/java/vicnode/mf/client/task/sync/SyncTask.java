package vicnode.mf.client.task.sync;

import java.nio.file.Path;
import java.util.logging.Logger;

import arc.mf.client.ServerClient.Connection;
import vicnode.mf.client.task.AbstractTask;
import vicnode.mf.client.util.PathUtils;
import vicnode.mf.client.util.StringUtils;

public abstract class SyncTask extends AbstractTask {

    private Path _rootDir;
    private String _rootNS;

    protected SyncTask(Connection cxn, Logger logger, Path rootDir, String rootNS) {
        super(cxn, logger);
        _rootDir = rootDir;
        _rootNS = rootNS;
    }

    public final String rootNamespace() {
        return _rootNS;
    }

    public final Path rootDirectory() {
        return _rootDir;
    }

    protected static String relativePath(Path parent, Path child) {
        return parent.toAbsolutePath().relativize(child.toAbsolutePath()).toString();
    }

    protected static String relativePath(String parent, String child) {
        return PathUtils.trimLeadingSlash(StringUtils.trimPrefix(child, parent, false));
    }

}
