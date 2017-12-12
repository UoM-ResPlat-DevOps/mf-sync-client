package resplat.mf.client.task.sync;

import resplat.mf.client.MFSession;

public class PoisonTask extends SyncTask {

    public PoisonTask() {
        super(null, null, null, null);
    }

    @Override
    public void execute(MFSession session) throws Throwable {
        throw new UnsupportedOperationException(this.getClass().getSimpleName()
                + " is not supposed to be executed. It is to poison the consumer thread.");

    }

    @Override
    public String type() {
        return "poison";
    }

}
