package vicnode.mf.client.task.sync;

import java.nio.file.Path;
import java.util.logging.Logger;

import arc.xml.XmlStringWriter;
import vicnode.mf.client.MFSession;
import vicnode.mf.client.util.PathUtils;

public class AssetDestroyTask extends SyncTask {

    private String _assetPath;

    public AssetDestroyTask(MFSession session, Logger logger, Path rootDir, String assetPath, String rootNS) {
        super(session, logger, rootDir, rootNS);
        _assetPath = assetPath;
    }

    public AssetDestroyTask(MFSession session, Logger logger, Path file, Path rootDir, String rootNS) {
        this(session, logger, rootDir, PathUtils.join(rootNS, relativePath(rootDir, file)), rootNS);
    }

    @Override
    public void execute(MFSession session) throws Throwable {
        setCurrentOperation("Deleting asset: " + _assetPath);
        logInfo("Deleting asset: '" + _assetPath + "'");
        setWorkTotal(1);
        setWorkProgressed(0);
        XmlStringWriter w = new XmlStringWriter();
        w.add("id", "path=" + _assetPath);
        session.execute("asset.soft.destroy", w.document(), null, null, this);
        setWorkProgressed(1);
    }

    @Override
    public String type() {
        return "asset.destroy";
    }
}
