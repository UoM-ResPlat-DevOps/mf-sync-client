package unimelb.mf.client.sync.task;

import java.nio.file.Path;
import java.util.logging.Logger;

import unimelb.mf.client.session.MFSession;
import unimelb.mf.client.util.AssetNamespaceUtils;
import unimelb.mf.client.util.PathUtils;

public class AssetNamespaceCreateTask extends SyncTask {

    private String _ns;

    public AssetNamespaceCreateTask(MFSession session, Logger logger, Path dir, Path rootDir, String rootNS) {
        super(session, logger, rootDir, rootNS);
        _ns = PathUtils.join(rootNamespace(), PathUtils.relativePath(rootDirectory(), dir));
    }

    @Override
    public void execute(MFSession session) throws Throwable {
        AssetNamespaceUtils.createAssetNamespace(session, _ns, logger());
    }

    @Override
    public final String type() {
        return "asset.namespace.create";
    }

}
