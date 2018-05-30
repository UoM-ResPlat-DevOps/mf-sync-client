package unimelb.mf.client.sync.task;

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
import unimelb.mf.client.file.PosixAttributes;
import unimelb.mf.client.session.MFSession;
import unimelb.mf.client.util.PathUtils;

public class FileUploadTask extends SyncTask {

    private FileUploadListener _ul;

    private Path _file;

    private boolean _csumCheck;

    private long _csum;

    private int _retry = 1;

    private long _bytesUploaded = 0;

    private String _assetPath;

    private String _assetId;

    public FileUploadTask(MFSession session, Logger logger, Path file, Path rootDir, String rootNS, boolean csumCheck,
            FileUploadListener ul) {
        super(session, logger, rootDir, rootNS);
        _file = file;
        _assetPath = PathUtils.join(rootNS, PathUtils.relativePath(rootDir, _file));
        _csumCheck = csumCheck;
        _csum = 0;
        _ul = ul;
    }

    public String assetPath() {
        return _assetPath;
    }

    void setAssetId(String assetId) {
        _assetId = assetId;
    }

    public Path file() {
        return _file;
    }

    @Override
    public void execute(MFSession session) throws Throwable {
        try {
            if (_ul != null) {
                _ul.fileUploadStarted(_file);
            }
            PosixAttributes fileAttrs = null;
            long fileSize = Files.size(_file);

            XmlStringWriter w2 = new XmlStringWriter();
            w2.push("service", new String[] { "name", "asset.set" });
            w2.add("id", "path=" + _assetPath);
            w2.add("create", true);
            w2.push("meta", new String[] { "action", "replace" });
            if (fileAttrs == null) {
                fileAttrs = PosixAttributes.read(_file);
            }
            fileAttrs.save(w2);
            w2.pop();
            w2.pop();

            if (_csumCheck) {
                w2.push("service", new String[] { "name", "asset.get" });
                w2.add("id", "path=" + _assetPath);
                w2.pop();
            }

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
                                _bytesUploaded += len;
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
            setCurrentOperation("Uploading file: '" + _file + "' to asset: '" + _assetPath + "'");
            logInfo("Uploading file: '" + _file + "' to asset: '" + _assetPath + "'");
            XmlDoc.Element re = session.execute("service.execute", w2.document(), input, null, this);
            if (_csumCheck) {
                XmlDoc.Element ae = re.element("reply[@service='asset.get']/response/asset");
                _assetId = re.value("id");
                long assetCSUM = ae.longValue("content/csum[@base='16']", 0L, 16);
                if (_csum != assetCSUM) {
                    logWarning("CRC32 checksums do not match for file: '" + _file + "'(" + _csum + ") and asset: '"
                            + _assetPath + "'(" + assetCSUM + ")");
                    _csum = 0;
                    if (_retry > 0) {
                        _retry--;
                        rewindProgress();
                        execute(session);
                        return;
                    } else {
                        throw new Exception("CRC32 checksums do not match for file: '" + _file + "'(" + _csum
                                + ") and asset: '" + _assetPath + "'(" + assetCSUM + ")");
                    }
                }
            } else {
                if (re.elementExists("reply[@service='asset.set']/response/id")) {
                    _assetId = re.value("reply[@service='asset.set']/response/id");
                } else if (re.elementExists("reply[@service='asset.set']/response/version")) {
                    _assetId = re.value("reply[@service='asset.set']/response/version/@id");
                }
            }
            if (_ul != null) {
                _ul.fileUploadCompleted(_file, _assetId);
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

    @Override
    public final String type() {
        return "file.upload";
    }

}
