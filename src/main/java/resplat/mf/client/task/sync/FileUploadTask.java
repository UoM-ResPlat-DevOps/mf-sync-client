package resplat.mf.client.task.sync;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import arc.mf.client.ServerClient;
import arc.streams.StreamCopy.AbortCheck;
import arc.xml.XmlDoc;
import arc.xml.XmlStringWriter;
import resplat.mf.client.MFSession;
import resplat.mf.client.file.PosixAttributes;
import resplat.mf.client.util.PathUtils;

public class FileUploadTask extends SyncTask {

    private Path _file;

    private String _service;

    public FileUploadTask(MFSession session, Logger logger, Path file, Path rootDir, String rootNS) {
        super(session, logger, rootDir, rootNS);
        _file = file;
    }

    @Override
    public void execute(MFSession session) throws Throwable {
        PosixAttributes fileAttrs = null;
        long fileSize = Files.size(_file);
        String assetPath = PathUtils.join(rootNamespace(), relativePath(rootDirectory(), _file));
        String assetId = null;
        // check if asset exists
        XmlStringWriter w1 = new XmlStringWriter();
        w1.add("id", "path=" + assetPath);
        boolean assetExists = session.execute("asset.exists", w1.document(), null, null, this).booleanValue("exists");
        boolean softDestroyed = false;
        if (assetExists) {
            setCurrentOperation("Retrieving metadata of asset: " + assetPath);
            XmlDoc.Element ae = session.execute("asset.get", w1.document(), null, null, this).element("asset");
            softDestroyed = ae.booleanValue("@destroyed", false);
            assetId = ae.value("@id");
            if (ae.elementExists("content") && ae.elementExists("meta/" + PosixAttributes.DOC_TYPE)) {
                long assetContentSize = ae.longValue("content/size");
                if (assetContentSize == fileSize) {
                    PosixAttributes attrs = new PosixAttributes(ae.element("meta/" + PosixAttributes.DOC_TYPE));
                    fileAttrs = PosixAttributes.read(_file);
                    if ((fileAttrs.mtimeEquals(attrs) || fileAttrs.mtimeLessThan(attrs))) {
                        // local file mtime <= server file mtime
                        // file on the server side is newer
                        logInfo("Skipped file: '" + _file + "'. Asset: '" + assetPath + "' already exists.");
                        setWorkTotal(1);
                        setWorkProgressed(1);
                        return;
                    }
                }
            }
        }
        XmlStringWriter w2 = new XmlStringWriter();
        if (assetExists) {
            _service = "asset.set";
            w2.add("id", assetId == null ? ("path=" + assetPath) : assetId);
            w2.push("meta", new String[] { "action", "replace" });
        } else {
            _service = "asset.create";
            String assetNamespace = PathUtils.getParentPath(assetPath);
            String assetName = PathUtils.getLastComponent(assetPath);
            w2.add("namespace", new String[] { "create", "true" }, assetNamespace);
            w2.add("name", assetName);
            w2.push("meta");
        }

        if (fileAttrs == null) {
            fileAttrs = PosixAttributes.read(_file);
        }
        fileAttrs.save(w2);
        w2.pop();
        String fileExt = PathUtils.getFileExtension(_file.toString());
        ServerClient.Input input = new ServerClient.GeneratedInput(null, fileExt, _file.toString(), fileSize) {
            @Override
            protected void copyTo(OutputStream out, AbortCheck ac) throws Throwable {
                try {
                    InputStream in = new BufferedInputStream(new FileInputStream(_file.toFile()));
                    byte[] buffer = new byte[8192];
                    int len;
                    try {
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                            incWorkProgress(len);
                            if ((ac != null && ac.hasBeenAborted()) || Thread.interrupted()) {
                                throw new InterruptedException("Upload aborted.");
                            }
                        }
                    } finally {
                        in.close();
                    }
                } finally {
                    out.close();
                }
            }
        };
        setCurrentOperation("Uploading file: " + _file + " (" + (assetExists ? "Updating" : "Creating") + " asset: "
                + assetPath + ")");
        logInfo("Uploading file: '" + _file + "' to asset: '" + (assetId == null ? assetPath : assetId) + "'");
        XmlDoc.Element re = session.execute(_service, w2.document(), input, null, this);
        if (re.elementExists("id")) {
            assetId = re.value("id");
        }
        if (softDestroyed) {
            XmlStringWriter w3 = new XmlStringWriter();
            w3.add("id", assetId == null ? ("path=" + assetPath) : assetId);
            session.execute("asset.soft.undestroy", w3.document(), null, null, this);
        }
    }

    @Override
    public final String type() {
        return "file.upload";
    }

}
