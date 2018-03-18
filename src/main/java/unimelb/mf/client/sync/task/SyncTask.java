package unimelb.mf.client.sync.task;

import java.nio.file.Path;
import java.util.logging.Logger;

import unimelb.mf.client.session.MFSession;
import unimelb.mf.client.task.AbstractTask;

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

}
