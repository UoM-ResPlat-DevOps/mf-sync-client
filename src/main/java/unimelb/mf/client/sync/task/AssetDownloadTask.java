package resplat.mf.client.sync.task;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import arc.mf.client.ServerClient;
import arc.streams.LongInputStream;
import arc.xml.XmlDoc.Element;
import arc.xml.XmlStringWriter;
import resplat.mf.client.file.PosixAttributes;
import resplat.mf.client.session.MFSession;
import resplat.mf.client.util.PathUtils;

public class AssetDownloadTask extends SyncTask {

    public final int BUFFER_SIZE = 8192;

    private String _assetId;
    private String _assetPath;
    private long _assetPosixMTime;

    public AssetDownloadTask(MFSession session, Logger logger, Path rootDir, String assetId, String assetPath,
            long assetPosixMTime, String rootNS) {
        super(session, logger, rootDir, rootNS);
        _assetId = assetId;
        _assetPath = assetPath;
        _assetPosixMTime = assetPosixMTime;
    }

    @Override
    public void execute(MFSession session) throws Throwable {

        Path file = Paths.get(PathUtils.normalise(rootDirectory().toString()),
                PathUtils.relativePath(rootNamespace(), _assetPath));
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

        setCurrentOperation("Downloading asset: " + (_assetId == null ? _assetPath : _assetId));
        session.execute("asset.get", w.document(), null, new ServerClient.OutputConsumer() {

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
        }, this);
    }

    @Override
    public String type() {
        return "asset.download";
    }

}
