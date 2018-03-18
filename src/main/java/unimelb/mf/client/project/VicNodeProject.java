package unimelb.mf.client.project;

import arc.xml.XmlStringWriter;
import unimelb.mf.client.session.MFSession;

public class VicNodeProject {

    public static final String DICT_PROJECT_NAMESPACE_MAP = "VicNode-Admin:project-namespace-map";

    public static String getProjectNamespace(MFSession session, int projectNumber) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("ordinal", projectNumber);
        return session.execute("vicnode.project.find", w.document(), null, null).value("project/@namespace");
    }

}
