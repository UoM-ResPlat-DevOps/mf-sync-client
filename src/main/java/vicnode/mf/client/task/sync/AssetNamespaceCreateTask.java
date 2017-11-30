package vicnode.mf.client.task.sync;

import java.nio.file.Path;
import java.util.logging.Logger;

import arc.xml.XmlStringWriter;
import vicnode.mf.client.MFSession;
import vicnode.mf.client.util.LoggingUtils;
import vicnode.mf.client.util.PathUtils;

public class AssetNamespaceCreateTask extends SyncTask {

    private String _ns;

    public AssetNamespaceCreateTask(MFSession session, Logger logger, Path dir, Path rootDir, String rootNS) {
        super(session, logger, rootDir, rootNS);
        _ns = PathUtils.join(rootNamespace(), relativePath(rootDirectory(), dir));
    }

    @Override
    public void execute(MFSession session) throws Throwable {
        createNamespace(session, _ns, logger());
    }

    @Override
    public final String type() {
        return "asset.namespace.create";
    }

    public static void createNamespace(MFSession session, String ns, Logger logger) throws Throwable {
        if (!namespaceExists(session, ns)) {
            try {
                LoggingUtils.logInfo(logger, "Creating asset namespace: '" + ns + "'");
                XmlStringWriter w = new XmlStringWriter();
                w.add("namespace", ns);
                session.execute("asset.namespace.create", w.document(), null, null);
            } catch (Throwable e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("already exists") || msg.contains("not accessible"))) {
                    LoggingUtils.logInfo(logger, "Asset namespace: '" + ns + "' already exists.");
                } else {
                    throw e;
                }
            }
        }
    }

    private static boolean namespaceExists(MFSession session, String ns) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("namespace", ns);
        return session.execute("asset.namespace.exists", w.document(), null, null).booleanValue("exists");
    }
}
