package resplat.mf.client.project;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;

import arc.xml.XmlDoc;
import arc.xml.XmlStringWriter;
import resplat.mf.client.session.MFSession;

public class VicNodeProject {

    public static final String DICT_PROJECT_NAMESPACE_MAP = "VicNode-Admin:project-namespace-map";

    public static String getProjectNamespace(MFSession session, int projectNumber) throws Throwable {
        SimpleEntry<String, String> entry = findProject(session, ".*\\." + projectNumber + "$");
        if (entry != null) {
            return entry.getValue();
        }
        return null;
    }

    public static SimpleEntry<String, String> findProject(MFSession session, String regex) throws Throwable {
        int idx = 1;
        int size = 1000;
        boolean complete = false;

        do {
            XmlStringWriter w = new XmlStringWriter();
            w.add("idx", idx);
            w.add("size", size);
            w.add("count", true);
            w.add("dictionary", DICT_PROJECT_NAMESPACE_MAP);
            XmlDoc.Element re = session.execute("dictionary.entries.describe", w.document(), null, null);
            List<XmlDoc.Element> ees = re.elements("entry");
            if (ees != null) {
                for (XmlDoc.Element ee : ees) {
                    String projectID = ee.value("term");
                    if (projectID.matches(regex)) {
                        String projectNS = ee.value("definition");
                        return new SimpleEntry<String, String>(projectID, projectNS);
                    }
                }
            }
            idx = re.intValue("cursor/next");
            complete = re.booleanValue("cursor/total/@complete");
        } while (!complete);
        return null;
    }

}
