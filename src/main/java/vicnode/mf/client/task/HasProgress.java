package vicnode.mf.client.task;

public interface HasProgress {

    long workTotal();

    long workProgressed();

    double progress();

    String currentOperation();

}
