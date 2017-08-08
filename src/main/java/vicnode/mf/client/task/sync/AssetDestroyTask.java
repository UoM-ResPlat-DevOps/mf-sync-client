package vicnode.mf.client.task.sync;

import java.nio.file.Path;
import java.util.logging.Logger;

import arc.mf.client.ServerClient.Connection;
import vicnode.mf.client.util.AssetUtils;
import vicnode.mf.client.util.PathUtils;

public class AssetDestroyTask extends SyncTask {

    private String _assetPath;

    public AssetDestroyTask(Connection cxn, Logger logger, Path rootDir, String assetPath, String rootNS) {
        super(cxn, logger, rootDir, rootNS);
        _assetPath = assetPath;
    }

    public AssetDestroyTask(Connection cxn, Logger logger, Path file, Path rootDir, String rootNS) {
        this(cxn, logger, rootDir, PathUtils.join(rootNS, relativePath(rootDir, file)), rootNS);
    }

    @Override
    public void execute(Connection cxn) throws Throwable {
        setCurrentOperation("Deleting asset: " + _assetPath);
        logInfo("Deleting asset: '" + _assetPath + "'");
        setWorkTotal(1);
        setWorkProgressed(0);
        AssetUtils.destroyAsset(cxn, "path=" + _assetPath, true);
        setWorkProgressed(1);
    }

    @Override
    public String type() {
        return "asset.destroy";
    }
}
