package resplat.mf.client.sync.task;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import arc.mf.client.ServerClient;
import arc.streams.StreamCopy.AbortCheck;
import arc.xml.XmlDoc;
import arc.xml.XmlStringWriter;
import resplat.mf.client.file.PosixAttributes;
import resplat.mf.client.session.MFSession;
import resplat.mf.client.util.AssetUtils;
import resplat.mf.client.util.ChecksumUtils;
import resplat.mf.client.util.PathUtils;

public class FileUploadTask extends SyncTask {

    private FileUploadListener _ul;

    private Path _file;

    private String _service;

    private boolean _csumCheck;

    private long _csum;

    private int _retry = 1;

    private long _bytesUploaded = 0;

    public FileUploadTask(MFSession session, Logger logger, Path file, Path rootDir, String rootNS, boolean csumCheck,
            FileUploadListener ul) {
        super(session, logger, rootDir, rootNS);
        _file = file;
        _csumCheck = csumCheck;
        _csum = 0;
        _ul = ul;
    }

    @Override
    public void execute(MFSession session) throws Throwable {
        try {
            PosixAttributes fileAttrs = null;
            long fileSize = Files.size(_file);
            String assetPath = PathUtils.join(rootNamespace(), relativePath(rootDirectory(), _file));
            String assetId = null;
            // check if asset exists
            XmlStringWriter w1 = new XmlStringWriter();
            w1.add("id", "path=" + assetPath);
            boolean assetExists = session.execute("asset.exists", w1.document(), null, null, this)
                    .booleanValue("exists");
            boolean softDestroyed = false;
            if (assetExists) {
                setCurrentOperation("Retrieving metadata of asset: " + assetPath);
                XmlDoc.Element ae = session.execute("asset.get", w1.document(), null, null, this).element("asset");
                softDestroyed = ae.booleanValue("@destroyed", false);
                assetId = ae.value("@id");
                long assetCSUM = ae.longValue("content/csum[@base='16']", 0L, 16);
                if (ae.elementExists("content") && ae.elementExists("meta/" + PosixAttributes.DOC_TYPE)) {
                    long assetContentSize = ae.longValue("content/size");
                    if (assetContentSize == fileSize) {
                        PosixAttributes attrs = new PosixAttributes(ae.element("meta/" + PosixAttributes.DOC_TYPE));
                        fileAttrs = PosixAttributes.read(_file);
                        if ((fileAttrs.mtimeEquals(attrs) || fileAttrs.mtimeLessThan(attrs))) {
                            // local file mtime <= server file mtime
                            // file on the server side is newer
                            if (!_csumCheck || assetCSUM == ChecksumUtils.getCRC32Value(_file)) {
                                if (softDestroyed) {
                                    undestroy(session, assetId);
                                }
                                logInfo("Skipped file: '" + _file + "'. Asset: '" + assetPath + "' already exists.");
                                setWorkTotal(1);
                                setWorkProgressed(1);
                                if (_ul != null) {
                                    _ul.fileUploadSkipped(_file);
                                }
                                return;
                            }
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
                if (_csumCheck) {
                    w2.add("action", "get-meta");
                }
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
                        if (_csumCheck) {
                            in = new CheckedInputStream(in, new CRC32());
                        }
                        byte[] buffer = new byte[8192];
                        int len;
                        try {
                            while ((len = in.read(buffer)) != -1) {
                                out.write(buffer, 0, len);
                                incWorkProgress(len);
                                if (_ul != null) {
                                    _ul.fileUploadProgressed(len);
                                }
                                if ((ac != null && ac.hasBeenAborted()) || Thread.interrupted()) {
                                    throw new InterruptedException("Upload aborted.");
                                }
                            }
                        } finally {
                            in.close();
                        }
                        if (_csumCheck) {
                            _csum = ((CheckedInputStream) in).getChecksum().getValue();
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
            if (_csumCheck) {
                XmlDoc.Element ae = re.elementExists("asset") ? re.element("asset")
                        : AssetUtils.getAssetMeta(session, assetId == null ? ("path=" + assetPath) : assetId);
                assetId = re.value("id");
                long assetCSUM = ae.longValue("content/csum[@base='16']", 0L, 16);
                if (_csum != assetCSUM) {
                    logWarning("CRC32 checksums do not match for file: '" + _file + "'(" + _csum + ") and asset: '"
                            + (assetId == null ? assetPath : assetId) + "'(" + assetCSUM + ")");
                    _csum = 0;
                    if (_retry > 0) {
                        _retry--;
                        rewindProgress();
                        execute(session);
                        return;
                    } else {
                        throw new Exception("CRC32 checksums do not match for file: '" + _file + "'(" + _csum
                                + ") and asset: '" + (assetId == null ? assetPath : assetId) + "'(" + assetCSUM + ")");
                    }
                }
            } else {
                if (re.elementExists("id")) {
                    assetId = re.value("id");
                } else if (re.elementExists("version")) {
                    assetId = re.value("version/@id");
                } else if (re.elementExists("asset")) {
                    assetId = re.value("asset/@id");
                }
            }
            if (softDestroyed) {
                undestroy(session, assetId == null ? ("path=" + assetPath) : assetId);
            }
            if (_ul != null) {
                _ul.fileUploadCompleted(_file, assetId);
            }
        } catch (Throwable e) {
            if (_ul != null) {
                _ul.fileUploadFailed(_file);
            }
            rewindProgress();
            throw e;
        }
    }

    private void rewindProgress() {
        if (_bytesUploaded > 0) {
            if (_ul != null) {
                _ul.fileUploadProgressed(-1 * _bytesUploaded);
            }
            incWorkProgress(-1 * _bytesUploaded);
            _bytesUploaded = 0;
        }
    }

    private void undestroy(MFSession session, String assetId) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("id", assetId);
        session.execute("asset.soft.undestroy", w.document(), null, null, this);
    }

    @Override
    public final String type() {
        return "file.upload";
    }

}
