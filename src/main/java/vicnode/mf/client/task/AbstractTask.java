package vicnode.mf.client.task;

import java.util.logging.Level;
import java.util.logging.Logger;

import arc.mf.client.ServerClient;
import arc.mf.client.ServerClient.Connection;
import vicnode.mf.client.util.LoggingUtils;

public abstract class AbstractTask implements Task {

    private ServerClient.Connection _cxn;
    private State _state;
    private long _workTotal;
    private long _workProgressed;
    private String _currentOp;

    private Logger _logger;

    protected AbstractTask(ServerClient.Connection cxn, Logger logger) {
        _cxn = cxn;
        _logger = logger;
        _state = State.PENDING;
        _workTotal = -1;
        _workProgressed = 0;
    }

    @Override
    public Logger logger() {
        return _logger;
    }

    @Override
    public Void call() throws Exception {
        setState(State.EXECUTING);
        ServerClient.Connection cxn = connect();
        try {
            execute(cxn);
            setState(State.COMPLETED);
            return null;
        } catch (Throwable e) {
            setState(State.FAILED);
            if (e instanceof Exception) {
                throw (Exception) e;
            } else {
                throw new Exception(e);
            }
        }
    }

    @Override
    public void log(Level level, String message, Throwable thrown) {
        LoggingUtils.log(logger(), level, message, thrown);
    }

    @Override
    public void logInfo(String message) {
        log(Level.INFO, message, null);
    }

    @Override
    public void logWarning(String message) {
        log(Level.WARNING, message, null);
    }

    @Override
    public void logError(String message, Throwable thrown) {
        log(Level.SEVERE, message, thrown);
    }

    @Override
    public void logError(Throwable thrown) {
        log(Level.SEVERE, thrown.getMessage(), thrown);
    }

    @Override
    public void logError(String message) {
        log(Level.SEVERE, message, null);
    }

    @Override
    public final synchronized State state() {
        return _state;
    }

    public final synchronized void setState(State state) {
        _state = state;
    }

    @Override
    public Connection connect() {
        return _cxn;
    }

    @Override
    public long workTotal() {
        return _workTotal;
    }

    protected void setWorkTotal(long workTotal) {
        _workTotal = workTotal;
    }

    @Override
    public long workProgressed() {
        return _workProgressed;
    }

    protected void setWorkProgressed(long workProgressed) {
        _workProgressed = workProgressed;
    }

    protected void incWorkProgress(long increment) {
        setWorkProgressed(workProgressed() + increment);
    }

    @Override
    public double progress() {
        if (workTotal() > 0 && workProgressed() > 0) {
            return ((double) workProgressed()) / ((double) workTotal());
        }
        return 0;
    }

    protected void setLogger(Logger logger) {
        _logger = logger;
    }

    protected void setCurrentOperation(String currentOp) {
        _currentOp = currentOp;
    }

    @Override
    public String currentOperation() {
        return _currentOp;
    }

}
