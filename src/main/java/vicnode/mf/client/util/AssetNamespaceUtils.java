package vicnode.mf.client.util;

import arc.mf.client.ServerClient;
import arc.xml.XmlStringWriter;

public class AssetNamespaceUtils {

    public static boolean namespaceExists(ServerClient.Connection cxn, String namespace) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("namespace", namespace);
        return cxn.execute("asset.namespace.exists", w.document()).booleanValue("exists");
    }

    public static void softDestroyAllAssets(ServerClient.Connection cxn, String namespace) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("where", "namespace>='" + namespace + "'");
        w.add("action", "pipe");
        w.add("service", new String[] { "name", "asset.soft.destroy" });
        cxn.execute("asset.query", w.document());
    }

}
