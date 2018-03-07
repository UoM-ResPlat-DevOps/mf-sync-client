package resplat.mf.client.sync.task;

import java.nio.file.Path;
import java.util.logging.Logger;

import resplat.mf.client.session.MFSession;
import resplat.mf.client.task.AbstractTask;
import resplat.mf.client.util.PathUtils;
import resplat.mf.client.util.StringUtils;

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
        return PathUtils.normalise(parent.toAbsolutePath().relativize(child.toAbsolutePath()).toString());
    }

    protected static String relativePath(String parent, String child) {
        return PathUtils.trimLeadingSlash(StringUtils.trimPrefix(child, parent, false));
    }

}
