package unimelb.mf.client.sync.task;

import java.nio.file.Path;

public interface FileUploadListener {
    
    void fileUploadStarted(Path file);

    void fileUploadCompleted(Path file, String assetId);

    void fileUploadFailed(Path file);

    void fileUploadSkipped(Path file);
    
    void fileUploadProgressed(long increment);

}
