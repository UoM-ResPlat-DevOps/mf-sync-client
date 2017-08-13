package vicnode.mf.client.util;

import arc.utils.CanAbort;

public interface HasAbortableOperation {

    void setAbortableOperation(CanAbort ca);

    CanAbort abortableOperation();

}
