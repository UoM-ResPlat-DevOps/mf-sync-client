package resplat.mf.client.task.sync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

import arc.xml.XmlDoc;
import arc.xml.XmlStringWriter;
import resplat.mf.client.MFSession;
import resplat.mf.client.file.PosixAttributes;
import resplat.mf.client.util.LoggingUtils;

public class AssetSyncTaskProducer implements Runnable {

    public static final int DEFAULT_PAGE_SIZE = 100;

    public static enum Direction {
        LOCAL_TO_REMOTE, REMOTE_TO_LOCAL
    }

    private Direction _direction;

    private MFSession _session;
    private Logger _logger;
    private Path _rootDirectory;
    private String _rootNamespace;

    private BlockingQueue<SyncTask> _queue;

    private int _pageSize;

    public AssetSyncTaskProducer(MFSession session, Logger logger, Path rootDirectory, String rootNamespace,
            BlockingQueue<SyncTask> queue) {
        this(Direction.LOCAL_TO_REMOTE, session, logger, rootDirectory, rootNamespace, queue, DEFAULT_PAGE_SIZE);
    }

    public AssetSyncTaskProducer(Direction direction, MFSession session, Logger logger, Path rootDirectory,
            String rootNamespace, BlockingQueue<SyncTask> queue, int pageSize) {

        _direction = direction == null ? Direction.LOCAL_TO_REMOTE : direction;

        _session = session;
        _logger = logger;
        _rootDirectory = rootDirectory;
        _rootNamespace = rootNamespace;
        _queue = queue;
        _pageSize = pageSize;
    }

    public Direction direction() {
        return _direction;
    }

    public AssetSyncTaskProducer setDirection(Direction direction) {
        _direction = direction;
        return this;
    }

    @Override
    public void run() {

        try {
            int idx = 1;
            boolean completed = false;
            do {
                XmlStringWriter w = new XmlStringWriter();
                w.add("where", "namespace>='" + _rootNamespace + "'");
                w.add("action", "get-value");
                w.add("size", _pageSize);
                w.add("idx", idx);
                w.add("xpath", new String[] { "ename", "path" },
                        "string.format('%s/%s', xvalue('namespace'), choose(equals(xvalue('name'),null()), string.format('__asset_id__%s',xvalue('@id')),xvalue('name')))");
                w.add("xpath", new String[] { "ename", "csize" }, "content/size");
                w.add("xpath", new String[] { "ename", "csum" }, "content/csum");
                w.add("xpath", new String[] { "ename", "mtime" }, "meta/" + PosixAttributes.DOC_TYPE + "/mtime");
                XmlDoc.Element re = _session.execute("asset.query", w.document(), null, null, null);
                List<XmlDoc.Element> aes = re.elements("asset");
                if (aes != null) {
                    // check if corresponding files exist
                    List<String> assetsToDelete = new ArrayList<String>();
                    for (XmlDoc.Element ae : aes) {
                        String assetId = ae.value("@id");
                        String assetPath = ae.value("path");
                        long assetContentSize = ae.longValue("csize", -1);
                        if (assetContentSize < 0) {
                            if (_direction == Direction.LOCAL_TO_REMOTE) {
                                _queue.put(new AssetDestroyTask(_session, _logger, _rootDirectory, assetPath,
                                        _rootNamespace));
                            } else {
                                LoggingUtils.logInfo(_logger,
                                        "Skipped asset: " + assetPath + "(id=" + assetId + ") No asset content found.");
                            }
                            continue;
                        }
                        long assetPosixMTime = ae.longValue("mtime", -1);
                        if (assetPosixMTime < 0) {
                            if (_direction == Direction.LOCAL_TO_REMOTE) {
                                _queue.put(new AssetDestroyTask(_session, _logger, _rootDirectory, assetPath,
                                        _rootNamespace));
                            } else {
                                LoggingUtils.logInfo(_logger, "Skipped asset: " + assetPath + "(id=" + assetId
                                        + ") No asset meta/" + PosixAttributes.DOC_TYPE + " found.");
                            }
                            continue;
                        }
                        Path file = Paths.get(_rootDirectory.toString(),
                                SyncTask.relativePath(_rootNamespace, assetPath));
                        if (!Files.exists(file)) {
                            if (_direction == Direction.LOCAL_TO_REMOTE) {
                                assetsToDelete.add(assetId);
                            } else {
                                _queue.put(new AssetDownloadTask(_session, _logger, _rootDirectory, assetId, assetPath,
                                        assetPosixMTime, _rootNamespace));
                            }
                        } else {
                            if (_direction == Direction.REMOTE_TO_LOCAL) {
                                PosixAttributes fileAttrs = PosixAttributes.read(file);
                                if (assetPosixMTime > fileAttrs.mtime()) {
                                    _queue.put(new AssetDownloadTask(_session, _logger, _rootDirectory, assetId,
                                            assetPath, assetPosixMTime, _rootNamespace));
                                }
                            }
                        }
                    }

                    // delete assets
                    if (!assetsToDelete.isEmpty()) {
                        XmlStringWriter w2 = new XmlStringWriter();
                        for (String assetId : assetsToDelete) {
                            w2.add("id", assetId);
                        }
                        _session.execute("asset.soft.destroy", w2.document(), null, null, null);
                    }
                }
                completed = re.longValue("cursor/remaining") == 0;
            } while (!completed);
        } catch (Throwable e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LoggingUtils.logError(_logger, e);
        }

    }

}
