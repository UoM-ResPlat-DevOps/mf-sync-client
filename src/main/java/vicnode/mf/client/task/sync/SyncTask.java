package vicnode.mf.client.task.sync;

import java.nio.file.Path;
import java.util.logging.Logger;

import vicnode.mf.client.MFSession;
import vicnode.mf.client.task.AbstractTask;
import vicnode.mf.client.util.PathUtils;
import vicnode.mf.client.util.StringUtils;

public abstract class SyncTask extends AbstractTask {

    private Path _rootDir;
    private String _rootNS;

    protected SyncTask(MFSession session, Logger logger, Path rootDir, String rootNS) {
        super(session, logger);
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
