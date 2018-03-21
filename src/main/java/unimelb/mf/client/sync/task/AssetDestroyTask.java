package unimelb.mf.client.sync.task;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import arc.mf.client.ServerClient;
import arc.xml.XmlStringWriter;
import unimelb.mf.client.session.MFSession;
import unimelb.mf.client.util.PathUtils;

public class AssetDestroyTask extends SyncTask {

    private String _assetPath;

    public AssetDestroyTask(MFSession session, Logger logger, Path rootDir, String assetPath, String rootNS) {
        super(session, logger, rootDir, rootNS);
        _assetPath = assetPath;
    }

    public AssetDestroyTask(MFSession session, Logger logger, Path file, Path rootDir, String rootNS) {
        this(session, logger, rootDir, PathUtils.join(rootNS, PathUtils.relativePath(rootDir, file)), rootNS);
    }

    @Override
    public void execute(MFSession session) throws Throwable {
        setCurrentOperation("Deleting asset: " + _assetPath);
        logInfo("Deleting asset: '" + _assetPath + "'");
        setWorkTotal(1);
        setWorkProgressed(0);
        XmlStringWriter w = new XmlStringWriter();
        w.add("id", "path=" + _assetPath);
        session.execute("asset.soft.destroy", w.document(), (List<ServerClient.Input>) null, null, this);
        setWorkProgressed(1);
    }

    @Override
    public String type() {
        return "asset.destroy";
    }
}
