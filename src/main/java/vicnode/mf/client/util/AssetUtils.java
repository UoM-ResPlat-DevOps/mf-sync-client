package vicnode.mf.client.util;

import java.util.Collection;

import arc.mf.client.ServerClient;
import arc.mf.client.ServerClient.Connection;
import arc.xml.XmlDoc;
import arc.xml.XmlStringWriter;

public class AssetUtils {

    public static boolean assetExists(ServerClient.Connection cxn, String id) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("id", id);
        return cxn.execute("asset.exists", w.document()).booleanValue("exists");
    }

    public static XmlDoc.Element getAssetMeta(ServerClient.Connection cxn, String id) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("id", id);
        return cxn.execute("asset.get", w.document()).element("asset");
    }

    public static void destroyAsset(ServerClient.Connection cxn, String id, boolean soft) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("id", id);
        cxn.execute(soft ? "asset.soft.destroy" : "asset.destroy", w.document());
    }

    public static void destroyAssets(Connection cxn, Collection<String> assetIds, boolean softDestroy)
            throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        for (String assetId : assetIds) {
            w.add("id", assetId);
        }
        cxn.execute(softDestroy ? "asset.soft.destroy" : "asset.destroy", w.document());
    }

}
