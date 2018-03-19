package unimelb.mf.client.sync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import arc.xml.XmlDoc;
import unimelb.mf.client.project.VicNodeProject;
import unimelb.mf.client.session.MFSession;
import unimelb.mf.client.util.AssetNamespaceUtils;
import unimelb.mf.client.util.PathPattern;
import unimelb.mf.client.util.PathUtils;

/**
 * See src/main/config/mf-sync-properties.sample.xml
 * 
 * @author wliu5
 *
 */
public class MFSyncSettings {

    public static class Job {

        public static enum Type {
            UPLOAD, DOWNLOAD
        }

        private Type _type;

        private Set<String> _pathExcludes;
        private Set<String> _pathIncludes;
        private Path _dir;
        private String _ns;
        private int _projectNumber;
        private boolean _projectIsParent = false;

        public Job(Type type, Path dir, String ns, boolean isParentNS, Collection<String> includes,
                Collection<String> excludes) {
            this(type, dir, ns, isParentNS, 0, false, includes, excludes);
        }

        public Job(Type type, Path dir, int projectNumber, boolean projectIsParent, Collection<String> includes,
                Collection<String> excludes) {
            this(type, dir, null, false, projectNumber, projectIsParent, includes, excludes);
        }

        Job(Type type, Path dir, String ns, boolean isParentNS, int projectNumber, boolean projectIsParent,
                Collection<String> includes, Collection<String> excludes) {
            _type = type;
            _dir = dir == null ? null : dir.toAbsolutePath();
            if (ns == null || !isParentNS) {
                _ns = ns;
            } else {
                _ns = PathUtils.join(ns, _dir.getFileName().toString());
            }
            _projectNumber = projectNumber;
            _projectIsParent = projectIsParent;
            if (includes != null && !includes.isEmpty()) {
                _pathIncludes = new LinkedHashSet<String>();
                _pathIncludes.addAll(includes);
            }
            if (excludes != null && !excludes.isEmpty()) {
                _pathExcludes = new LinkedHashSet<String>();
                _pathExcludes.addAll(excludes);
            }
        }

        public Job(XmlDoc.Element je) throws Throwable {
            _type = Type.valueOf(je.stringValue("@type", "upload").toUpperCase());
            _dir = Paths.get(je.value("directory")).toAbsolutePath();
            boolean isParentNS = je.booleanValue("namespace/@parent", true);
            if (isParentNS) {
                String ns = _dir.getFileName().toString();
                _ns = PathUtils.join(je.value("namespace"), ns);
            } else {
                _ns = je.value("namespace");
            }
            _projectNumber = je.intValue("project", 0);
            _projectIsParent = je.booleanValue("project/@parent", false);
            if (je.elementExists("include")) {
                _pathIncludes = new LinkedHashSet<String>();
                _pathIncludes.addAll(je.values("include"));
            }
            if (je.elementExists("exclude")) {
                _pathExcludes = new LinkedHashSet<String>();
                _pathExcludes.addAll(je.values("exclude"));
            }
        }

        public Type type() {
            return _type;
        }

        public Path directory() {
            return _dir;
        }

        public String namespace() {
            return _ns;
        }

        void setProjectNamespace(String projectNamespace) {
            if (_projectIsParent) {
                _ns = PathUtils.join(projectNamespace, _dir.getFileName().toString());
            } else {
                _ns = projectNamespace;
            }
        }

        public int projectNumber() {
            return _projectNumber;
        }

        public boolean projectIsParent() {
            return _projectIsParent;
        }

        public Set<String> excludes() {
            return _pathExcludes != null ? Collections.unmodifiableSet(_pathExcludes) : null;
        }

        public Set<String> includes() {
            return _pathIncludes != null ? Collections.unmodifiableSet(_pathIncludes) : null;
        }

        public boolean matchPath(Path path) {
            if (!PathUtils.isOrIsDescendant(path, _dir)) {
                return false;
            }
            boolean haveIncludePatterns = _pathIncludes != null && !_pathIncludes.isEmpty();
            boolean haveExcludePatterns = _pathExcludes != null && !_pathExcludes.isEmpty();
            if (!haveIncludePatterns && !haveExcludePatterns) {
                return true;
            }
            String relativePath = PathUtils.relativePath(_dir, path);
            if (haveIncludePatterns) {
                if (haveExcludePatterns) {
                    return matchesAny(relativePath, _pathIncludes) && !matchesAny(relativePath, _pathExcludes);
                } else {
                    return matchesAny(relativePath, _pathIncludes);
                }
            } else {
                if (haveExcludePatterns) {
                    return !matchesAny(relativePath, _pathExcludes);
                } else {
                    return true;
                }
            }
        }

        static boolean matchesAny(String relativePath, Collection<String> patterns) {
            for (String pattern : patterns) {
                String regex = PathPattern.toRegEx(pattern);
                if (relativePath.matches(regex)) {
                    return true;
                }
            }
            return false;
        }

        public boolean matchPath(File path) {
            return matchPath(path.toPath());
        }

        public boolean directoryAndNamespaceEquals(Job job) {
            if (job != null) {
                if (this == job) {
                    return true;
                }
                return _dir != null && _ns != null && _dir.equals(job.directory()) && _ns.equals(job.namespace());
            }
            return false;
        }

        public String parentNamespace() {
            return PathUtils.getParentPath(_ns);
        }

    }

    private List<Job> _jobs;

    private int _numberOfWorkers = 1;
    private int _maxNumberOfCheckers = 4;
    private int _checkBatchSize = 100;
    private boolean _daemonEnabled = false;
    private int _daemonListenerPort = MFSync.DEFAULT_DAEMON_LISTENER_PORT;
    private int _daemonScanInterval = MFSync.DEFAULT_DAEMON_SCAN_INTERVAL;
    private boolean _csumCheck = false;
    private boolean _excludeEmptyFolder = false;
    private Path _logDirectory = MFSync.DEFAULT_LOG_DIR;
    private Set<String> _emailAddresses = null;

    public MFSyncSettings(XmlDoc.Element pe) throws Throwable {
        if (pe != null) {
            loadFromXml(pe);
        }
    }

    public MFSyncSettings(File xmlFile) throws Throwable {
        if (xmlFile != null && xmlFile.exists()) {
            loadFromXmlFile(xmlFile);
        }
    }

    public void loadFromXmlFile(File xmlFile) throws Throwable {
        Reader reader = new BufferedReader(new FileReader(xmlFile));
        try {
            XmlDoc.Element pe = new XmlDoc().parse(reader);
            if (pe != null) {
                loadFromXml(pe);
            }
        } finally {
            reader.close();
        }
    }

    private void loadFromXml(XmlDoc.Element pe) throws Throwable {
        XmlDoc.Element se = pe.element("sync");
        if (se == null) {
            throw new Exception(
                    "No sync element is found in the properties XML element. Invalid/Incomplete configuration file.");
        }
        _numberOfWorkers = se.intValue("settings/numberOfWorkers", 1);

        _maxNumberOfCheckers = se.intValue("settings/maxNumberOfCheckers", MFSync.DEFAULT_MAX_NUMBER_OF_CHECKERS);
        if (_maxNumberOfCheckers < 1 || _maxNumberOfCheckers > 8) {
            System.err.println("Invalid maxNumberOfCheckers: " + _maxNumberOfCheckers + ". Fall back to "
                    + MFSync.DEFAULT_MAX_NUMBER_OF_CHECKERS + ".");
            _maxNumberOfCheckers = MFSync.DEFAULT_MAX_NUMBER_OF_CHECKERS;
        }

        _checkBatchSize = se.intValue("settings/checkBatchSize", MFSync.DEFAULT_CHECK_BATCH_SIZE);
        if (_checkBatchSize < 1 || _checkBatchSize > 10000) {
            System.err.println("Invalid checkBatchSize: " + _checkBatchSize + ". Fall back to "
                    + MFSync.DEFAULT_CHECK_BATCH_SIZE + ".");
            _checkBatchSize = MFSync.DEFAULT_CHECK_BATCH_SIZE;
        }

        _daemonEnabled = se.booleanValue("settings/daemon/@enabled", false);
        _daemonListenerPort = se.intValue("settings/daemon/listenerPort", MFSync.DEFAULT_DAEMON_LISTENER_PORT);
        _daemonScanInterval = se.intValue("settings/daemon/scanInterval", MFSync.DEFAULT_DAEMON_SCAN_INTERVAL);
        _csumCheck = se.booleanValue("settings/csumCheck", false);
        _excludeEmptyFolder = se.booleanValue("settings/excludeEmptyFolder", false);
        _logDirectory = Paths.get(se.stringValue("settings/logDirectory", System.getProperty("user.dir")));
        setNotificationEmailAddresses(se.values("settings/notification/email"));
        List<XmlDoc.Element> jes = se.elements("job");
        if (jes == null || jes.isEmpty()) {
            throw new Exception(
                    "No job element is found in the properties/sync XML element. Invalid/Incomplete configuration file.");
        }
        _jobs = new ArrayList<Job>();
        for (XmlDoc.Element je : jes) {
            _jobs.add(new Job(je));
        }
    }

    public MFSyncSettings addUploadJob(Path directory, String namespace, boolean isParentNS) {
        return addUploadJob(directory, namespace, isParentNS, null, null);
    }

    public MFSyncSettings addUploadJob(Path directory, String namespace, boolean isParentNS,
            Collection<String> includes, Collection<String> excludes) {
        return addJob(new Job(Job.Type.UPLOAD, directory, namespace, isParentNS, includes, excludes));
    }

    public MFSyncSettings addUploadJob(Path directory, int projectNumber, boolean projectIsParent) {
        return addUploadJob(directory, projectNumber, projectIsParent, null, null);
    }

    public MFSyncSettings addUploadJob(Path directory, int projectNumber, boolean projectIsParent,
            Collection<String> includes, Collection<String> excludes) {
        return addJob(new Job(Job.Type.UPLOAD, directory, projectNumber, projectIsParent, includes, excludes));
    }

    public MFSyncSettings addJob(Job job) {
        Job replace = null;
        if (_jobs == null) {
            _jobs = new ArrayList<Job>();
        } else {
            for (Job j : _jobs) {
                if (j.directoryAndNamespaceEquals(job)) {
                    replace = j;
                    break;
                }
            }
        }
        if (replace != null) {
            _jobs.remove(replace);
        }
        _jobs.add(job);
        return this;
    }

    public List<Job> jobs() {
        return _jobs == null ? null : Collections.unmodifiableList(_jobs);
    }

    public MFSyncSettings setLogDirectory(Path logDir) {
        _logDirectory = logDir;
        return this;
    }

    public Path logDirectory() {
        return _logDirectory;
    }

    public MFSyncSettings setNumberOfWorkers(int nbWorkers) {
        if (nbWorkers > 1) {
            _numberOfWorkers = nbWorkers;
        }
        return this;
    }

    public int numberOfWorkers() {
        return _numberOfWorkers;
    }

    public MFSyncSettings setWatchDaemon(boolean watchDaemon) {
        _daemonEnabled = watchDaemon;
        return this;
    }

    public boolean daemonEnabled() {
        return _daemonEnabled;
    }

    public MFSyncSettings setDaemonListenerPort(int port) {
        _daemonListenerPort = port;
        return this;
    }

    public int daemonListenerPort() {
        return _daemonListenerPort;
    }

    public MFSyncSettings setDaemonScanInterval(int millisecs) {
        _daemonScanInterval = millisecs;
        return this;
    }

    public int daemonScanInterval() {
        return _daemonScanInterval;
    }

    public MFSyncSettings setMaxNumberOfCheckers(int maxNumberOfCheckers) {
        if (maxNumberOfCheckers >= 1 && maxNumberOfCheckers <= 8) {
            _maxNumberOfCheckers = maxNumberOfCheckers;
        } else {
            _maxNumberOfCheckers = MFSync.DEFAULT_MAX_NUMBER_OF_CHECKERS;
        }
        return this;
    }

    public int maxNumberOfCheckers() {
        return _maxNumberOfCheckers;
    }

    public MFSyncSettings setCheckBatchSize(int checkBatchSize) {
        if (checkBatchSize >= 1 && checkBatchSize <= 10000) {
            _checkBatchSize = checkBatchSize;
        } else {
            _checkBatchSize = MFSync.DEFAULT_CHECK_BATCH_SIZE;
        }
        return this;
    }

    public int checkBatchSize() {
        return _checkBatchSize;
    }

    public MFSyncSettings setCsumCheck(boolean csumCheck) {
        _csumCheck = csumCheck;
        return this;
    }

    public boolean csumCheck() {
        return _csumCheck;
    }

    public MFSyncSettings setExcludeEmptyFolder(boolean excludeEmptyFolder) {
        _excludeEmptyFolder = excludeEmptyFolder;
        return this;
    }

    public boolean excludeEmptyFolder() {
        return _excludeEmptyFolder;
    }

    public Collection<String> notificationEmailAddresses() {
        if (_emailAddresses != null && !_emailAddresses.isEmpty()) {
            return Collections.unmodifiableCollection(_emailAddresses);
        } else {
            return null;
        }
    }

    public boolean hasNotificationEmailAddresses() {
        return _emailAddresses != null && !_emailAddresses.isEmpty();
    }

    public void validate(MFSession session) throws Throwable {
        if (_jobs == null || _jobs.isEmpty()) {
            throw new IllegalArgumentException(
                    "No sync job, which specifies the source directory and the destination Mediaflux namespace,  is set.");
        }
        for (Job job : _jobs) {
            if (job.directory() == null || !Files.exists(job.directory()) || !Files.isDirectory(job.directory())) {
                throw new IllegalArgumentException("Invalid sync job direcotry: '" + job.directory().toString() + "'");
            }
            if (job.projectNumber() > 0) {
                String ns = VicNodeProject.getProjectNamespace(session, job.projectNumber());
                if (ns == null) {
                    throw new IllegalArgumentException(
                            "Destination project " + job.projectNumber() + " or its namespace does not exist.");
                }
                if (!AssetNamespaceUtils.assetNamespaceExists(session, ns)) {
                    throw new IllegalArgumentException("Destination project namespace: '" + ns + "' does not exist.");
                }
                job.setProjectNamespace(ns);
            } else {
                if (!AssetNamespaceUtils.assetNamespaceExists(session, job.parentNamespace())) {
                    throw new IllegalArgumentException(
                            "Destination (parent) namespace: '" + job.parentNamespace() + "' does not exist.");
                }
            }
        }
        if (_daemonEnabled) {
            for (Job job : _jobs) {
                if (PathUtils.isOrIsDescendant(_logDirectory, job.directory())) {
                    throw new IllegalArgumentException("log directory: '" + _logDirectory
                            + "' is contained by one of the job source directory. This will cause uploading log file infinitely. Modify properties/sync/logDirectory value in the config file or specify --log.dir argument to point to a different location to resolve the issue.");
                }
            }
        }
    }

    public List<Job> jobsMatchPath(Path path) {
        List<Job> r = new ArrayList<Job>();
        if (_jobs != null) {
            for (Job job : _jobs) {
                if (job.matchPath(path)) {
                    r.add(job);
                }
            }
        }
        if (!r.isEmpty()) {
            return r;
        }
        return null;
    }

    public MFSyncSettings copy(boolean includeJobs) throws Throwable {
        MFSyncSettings settings = new MFSyncSettings((XmlDoc.Element) null);
        settings.setNumberOfWorkers(_numberOfWorkers);
        settings.setWatchDaemon(_daemonEnabled);
        settings.setCsumCheck(_csumCheck);
        settings.setExcludeEmptyFolder(_excludeEmptyFolder);
        settings.setLogDirectory(_logDirectory);
        if (includeJobs && _jobs != null) {
            for (Job job : _jobs) {
                settings.addJob(job);
            }
        }
        return settings;
    }

    public void print(PrintStream ps) {
        ps.println();
        ps.println("Settings: ");
        if (_jobs != null) {
            for (Job job : _jobs) {
                ps.println("    src-directory: " + job.directory());
                if (job.includes() != null) {
                    Collection<String> includes = job.includes();
                    for (String include : includes) {
                        ps.println("        include: " + include);
                    }
                }
                if (job.excludes() != null) {
                    Collection<String> excludes = job.excludes();
                    for (String exclude : excludes) {
                        ps.println("        exclude: " + exclude);
                    }
                }
                ps.println("    dst-namespace: " + job.namespace());
                ps.println();
            }
        }
        ps.println("    number-of-workers:  " + _numberOfWorkers);
        ps.println("    daemon: " + _daemonEnabled);
        if (_daemonEnabled) {
            ps.println("    daemon-port: " + _daemonListenerPort);
        }
        ps.println("    csum-check: " + _csumCheck);
        ps.println("    exclude-empty-folder: " + _excludeEmptyFolder);
        ps.println("    log-directory: " + _logDirectory);
        if (this.hasNotificationEmailAddresses()) {
            ps.println("    notification: ");
            for (String email : _emailAddresses) {
                ps.println("        mail: " + email);
            }
        }
    }

    public void addNotificationEmailAddress(String emailAddr) {
        if (_emailAddresses == null) {
            _emailAddresses = new LinkedHashSet<String>();
        }
        if (emailAddr != null) {
            _emailAddresses.add(emailAddr);
        }
    }

    public void setNotificationEmailAddresses(Collection<String> emailAddresses) {
        if (_emailAddresses == null) {
            _emailAddresses = new LinkedHashSet<String>();
        } else {
            _emailAddresses.clear();
        }
        if (emailAddresses != null && !emailAddresses.isEmpty()) {
            _emailAddresses.addAll(emailAddresses);
        }
    }

}
