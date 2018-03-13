package resplat.mf.client.sync.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

import arc.xml.XmlDoc;
import arc.xml.XmlStringWriter;
import resplat.mf.client.file.PosixAttributes;
import resplat.mf.client.session.MFSession;
import resplat.mf.client.task.AbstractTask;
import resplat.mf.client.task.Task;

public class FileCheckTask extends AbstractTask {

    private Map<String, FileUploadTask> _tasks;
    private BlockingQueue<Task> _queue;
    private FileUploadListener _ul;

    public FileCheckTask(MFSession session, Logger logger, Collection<FileUploadTask> tasks, BlockingQueue<Task> queue,
            FileUploadListener ul) {
        super(session, logger);
        _queue = queue;
        _ul = ul;
        if (tasks != null && !tasks.isEmpty()) {
            _tasks = new LinkedHashMap<String, FileUploadTask>();
            for (FileUploadTask task : tasks) {
                _tasks.put(task.assetPath(), task);
            }
        }
    }

    @Override
    public void execute(MFSession session) throws Throwable {
        if (_tasks != null && !_tasks.isEmpty()) {
            this.logInfo("Checking " + _tasks.size() + " files...");
            XmlStringWriter w1 = new XmlStringWriter();
            Set<String> assetPaths = _tasks.keySet();
            for (String assetPath : assetPaths) {
                w1.add("id", "path=" + assetPath);
            }
            List<XmlDoc.Element> ees = session.execute("asset.exists", w1.document(), null, null).elements("exists");

            int nbExists = 0;
            XmlStringWriter w2 = new XmlStringWriter();
            for (XmlDoc.Element ee : ees) {
                String assetPath = ee.value("@id").replaceFirst("^path=", "");
                boolean exists = ee.booleanValue();
                if (!exists) {
                    _queue.put(_tasks.get(assetPath));
                } else {
                    w2.add("id", "path=" + assetPath);
                    nbExists++;
                }
            }
            if (nbExists > 0) {
                List<String> undestroy = new ArrayList<String>();
                List<FileUploadTask> update = new ArrayList<FileUploadTask>();
                List<XmlDoc.Element> aes = session.execute("asset.get", w2.document(), null, null).elements("asset");
                for (XmlDoc.Element ae : aes) {

                    String assetId = ae.value("@id");

                    /*
                     * undestroy if soft-destroyed
                     */
                    boolean softDestroyed = ae.booleanValue("@destroyed", false);
                    if (softDestroyed) {
                        undestroy.add(assetId);
                    }

                    String assetPath = ae.value("path");
                    FileUploadTask task = _tasks.get(assetPath);
                    Path file = task.file();

                    /*
                     * update if asset has no content
                     */
                    if (!ae.elementExists("content")) {
                        update.add(task);
                        continue;
                    }

                    /*
                     * update if file size differs asset content size
                     */
                    long contentSize = ae.longValue("content/size");
                    long fileSize = Files.size(file);
                    if (fileSize != contentSize) {
                        update.add(task);
                        continue;
                    }

                    /*
                     * update if local file posix mtime is greater
                     */
                    PosixAttributes contentAttrs = ae.elementExists("meta/" + PosixAttributes.DOC_TYPE)
                            ? new PosixAttributes(ae.element("meta/" + PosixAttributes.DOC_TYPE))
                            : null;
                    PosixAttributes fileAttrs = PosixAttributes.read(file);
                    if (contentAttrs != null && fileAttrs != null && fileAttrs.mtimeGreaterThan(contentAttrs)) {
                        // local file mtime > asset content posix mtime
                        update.add(task);
                        continue;
                    }

                    // @formatter:off
                    /*
                     * update if csums differ
                     */
//                    long contentCSUM = ae.longValue("content/csum[@base='16']", 0L, 16);
//                    if (contentCSUM != ChecksumUtils.getCRC32Value(file)) {
//                        update.add(task);
//                        continue;
//                    }
                    // @formatter:on

                    logInfo("Skipped file: '" + file + "'. Asset: '" + assetPath + "' already exists.");
                    if (_ul != null) {
                        _ul.fileUploadSkipped(file);
                    }
                }
                if (!undestroy.isEmpty()) {
                    XmlStringWriter w3 = new XmlStringWriter();
                    for (String assetId : undestroy) {
                        w3.add("id", assetId);
                    }
                    logInfo("undestroy soft-destroyed assets...");
                    session.execute("asset.soft.undestroy", w3.document(), null, null);
                }
                if (!update.isEmpty()) {
                    for (FileUploadTask task : update) {
                        _queue.put(task);
                    }
                }
            }
            setWorkProgressed(_tasks.size());
        }
    }

    @Override
    public String type() {
        return "file.check";
    }

}
