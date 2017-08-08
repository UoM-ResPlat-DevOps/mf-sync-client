package vicnode.mf.client.task.sync;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import arc.mf.client.RequestOptions;
import arc.mf.client.ServerClient;
import arc.mf.client.ServerClient.Connection;
import arc.streams.LongInputStream;
import arc.utils.AbortableOperationHandler;
import arc.utils.CanAbort;
import arc.xml.XmlDoc.Element;
import arc.xml.XmlStringWriter;
import vicnode.mf.client.file.PosixAttributes;

public class AssetDownloadTask extends SyncTask {

    public final int BUFFER_SIZE = 8192;

    private String _assetId;
    private String _assetPath;
    private long _assetPosixMTime;

    private CanAbort _ca;

    public AssetDownloadTask(Connection cxn, Logger logger, Path rootDir, String assetId, String assetPath, long assetPosixMTime,
            String rootNS) {
        super(cxn, logger, rootDir, rootNS);
        _assetId = assetId;
        _assetPath = assetPath;
        _assetPosixMTime = assetPosixMTime;
    }

    @Override
    public void execute(Connection cxn) throws Throwable {

        Path file = Paths.get(rootDirectory().toString(), relativePath(rootNamespace(), _assetPath));
        boolean fileExists = Files.exists(file);
        if (fileExists) {
            PosixAttributes fileAttrs = PosixAttributes.read(file);
            if (_assetPosixMTime <= fileAttrs.mtime()) {
                setWorkTotal(1);
                setWorkProgressed(1);
                logInfo("Skipped downloading asset: " + (_assetId == null ? _assetPath : _assetId) + ". File: " + file
                        + " already exists.");
                return;
            }
        }

        XmlStringWriter w = new XmlStringWriter();
        w.add("id", _assetId == null ? ("path=" + _assetPath) : _assetId);
        RequestOptions ro = new RequestOptions();
        ro.setAbortHandler(new AbortableOperationHandler() {

            public void finished(CanAbort ca) {
                _ca = null;
            }

            public void started(CanAbort ca) {
                _ca = ca;
            }
        });

        try {
            setCurrentOperation("Downloading asset: " + (_assetId == null ? _assetPath : _assetId));
            cxn.executeMultiInput(null, "asset.get", w.document(), null, new ServerClient.OutputConsumer() {

                @Override
                protected void consume(Element re, LongInputStream in) throws Throwable {
                    if (workTotal() < 0) {
                        setWorkTotal(re.longValue("asset/content/size"));
                    }
                    try {
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(file.toFile()));
                        try {
                            byte[] buffer = new byte[BUFFER_SIZE];
                            int len;
                            while ((len = in.read(buffer)) != -1) {
                                out.write(buffer, 0, len);
                                incWorkProgress(len);
                                if (Thread.interrupted()) {
                                    throw new InterruptedException(
                                            "Aborted downloading asset: " + (_assetId == null ? _assetPath : _assetId));
                                }
                            }
                        } finally {
                            out.close();
                        }
                    } finally {
                        in.close();
                    }
                }
            }, ro);
        } catch (InterruptedException e) {
            if (_ca != null) {
                try {
                    _ca.abort();
                } catch (Throwable t2) {
                    log(Level.WARNING, "Failed to abort service: asset.get", t2);
                }
            }
            throw e;
        }

    }

    @Override
    public String type() {
        return "asset.download";
    }

}
