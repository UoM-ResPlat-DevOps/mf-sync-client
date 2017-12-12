package resplat.mf.client.task.sync;

import java.nio.file.Path;
import java.util.logging.Logger;

import resplat.mf.client.MFSession;
import resplat.mf.client.util.AssetNamespaceUtils;
import resplat.mf.client.util.PathUtils;

public class AssetNamespaceCreateTask extends SyncTask {

    private String _ns;

    public AssetNamespaceCreateTask(MFSession session, Logger logger, Path dir, Path rootDir, String rootNS) {
        super(session, logger, rootDir, rootNS);
        _ns = PathUtils.join(rootNamespace(), relativePath(rootDirectory(), dir));
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
