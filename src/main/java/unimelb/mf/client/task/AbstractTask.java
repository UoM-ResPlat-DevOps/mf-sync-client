package unimelb.mf.client.task;

import java.util.logging.Level;
import java.util.logging.Logger;

import arc.utils.CanAbort;
import unimelb.mf.client.session.MFSession;
import unimelb.mf.client.util.LoggingUtils;

public abstract class AbstractTask implements Task {

    private MFSession _session;
    private State _state;
    private long _workTotal;
    private long _workProgressed;
    private String _currentOp;

    private Logger _logger;

    private CanAbort _ca;

    protected AbstractTask(MFSession session, Logger logger) {
        _session = session;
        _logger = logger == null ? LoggingUtils.createConsoleLogger() : logger;
        _state = State.PENDING;
        _workTotal = -1;
        _workProgressed = 0;
    }

    public CanAbort abortableOperation() {
        return _ca;
    }

    public void setAbortableOperation(CanAbort ca) {
        _ca = ca;
    }

    @Override
    public final Logger logger() {
        return _logger;
    }

    @Override
    public Void call() throws Exception {
        setState(State.EXECUTING);
        try {
            execute(_session);
            setState(State.COMPLETED);
            return null;
        } catch (Throwable e) {
            setState(State.FAILED);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                CanAbort ca = abortableOperation();
                if (ca != null) {
                    try {
                        ca.abort();
                    } catch (Throwable e1) {
                        logError("Fail to abort service call.", e1);
                    }
                }
            }
            if (e instanceof Exception) {
                throw (Exception) e;
            } else {
                throw new Exception(e);
            }
        }
    }

    @Override
    public final void log(Level level, String message, Throwable thrown) {
        _logger.log(level, message, thrown);
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

    protected void setCurrentOperation(String currentOp) {
        _currentOp = currentOp;
    }

    @Override
    public String currentOperation() {
        return _currentOp;
    }

}
