package vicnode.mf.client;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import arc.mf.client.AuthenticationDetails;
import arc.mf.client.RemoteServer;
import arc.mf.client.RequestOptions;
import arc.mf.client.ServerClient;
import arc.utils.AbortableOperationHandler;
import arc.utils.CanAbort;
import arc.xml.XmlDoc;
import vicnode.mf.client.util.HasAbortableOperation;

public class MFSession {

    private ConnectionSettings _settings;

    private RemoteServer _rs;
    private AuthenticationDetails _auth;
    private String _sessionId;
    private Timer _timer;

    public MFSession(ConnectionSettings settings) {
        _settings = settings;
    }

    public synchronized String sessionId() {
        return _sessionId;
    }

    private synchronized void setSessionId(String sessionId) {
        _sessionId = sessionId;
    }

    public synchronized ServerClient.Connection connect() throws Throwable {
        if (_rs == null) {
            _rs = new RemoteServer(_settings.serverHost(), _settings.serverPort(), _settings.useHttp(),
                    _settings.encrypt());
            _rs.setConnectionPooling(true);
        }
        ServerClient.Connection cxn = _rs.open();
        String sessionId = sessionId();
        if (sessionId != null) {
            cxn.reconnect(sessionId);
        } else {
            setSessionId(cxn.connect(_auth == null ? _settings.authenticationDetails() : _auth));
        }
        if (_auth == null) {
            _auth = cxn.authenticationDetails();
        }
        return cxn;
    }

    public XmlDoc.Element execute(String service, String args, ServerClient.Input input, ServerClient.Output output,
            HasAbortableOperation abortable) throws Throwable {
        ServerClient.Connection cxn = connect();
        try {
            return execute(cxn, service, args, input, output, abortable, 1);
        } finally {
            cxn.close();
        }
    }

    private XmlDoc.Element execute(ServerClient.Connection cxn, String service, String args, ServerClient.Input input,
            ServerClient.Output output, HasAbortableOperation abortable, int retry) throws Throwable {
        try {
            RequestOptions ops = new RequestOptions();
            ops.setAbortHandler(new AbortableOperationHandler() {

                @Override
                public void finished(CanAbort ca) {
                    if (abortable != null) {
                        abortable.setAbortableOperation(null);
                    }
                }

                @Override
                public void started(CanAbort ca) {
                    if (abortable != null) {
                        abortable.setAbortableOperation(ca);
                    }
                }
            });
            return cxn.executeMultiInput(null, service, args, input == null ? null : Arrays.asList(input), output, ops);
        } catch (ServerClient.ExSessionInvalid si) {
            if (_auth != null && retry > 0) {
                setSessionId(cxn.connect(_auth));
                return execute(cxn, service, args, input, output, abortable, --retry);
            }
            throw si;
        }
    }

    public XmlDoc.Element execute(String service, String args, ServerClient.Input input, ServerClient.Output output)
            throws Throwable {
        return execute(service, args, input, output, null);
    }

    public void discard() {
        if (_rs != null) {
            _rs.discard();
        }
        stopPingServerPeriodically();
    }

    public void startPingServerPeriodically(int period) {
        stopPingServerPeriodically();
        _timer = new Timer();
        _timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                try {
                    execute("server.ping", null, null, null);
                } catch (Throwable e) {
                    e.printStackTrace(System.err);
                }
            }
        }, 0, period);
    }

    public void stopPingServerPeriodically() {
        if (_timer != null) {
            _timer.cancel();
            _timer = null;
        }
    }

}
