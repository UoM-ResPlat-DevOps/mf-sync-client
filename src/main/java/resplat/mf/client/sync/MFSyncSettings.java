package resplat.mf.client.sync;

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
import resplat.mf.client.session.MFSession;
import resplat.mf.client.util.AssetNamespaceUtils;
import resplat.mf.client.util.PathUtils;

/**
 * See src/main/config/mf-sync-properties.sample.xml
 * 
 * @author wliu5
 *
 */
public class MFSyncSettings {

    public static class Job {

        private Set<String> _pathExcludes;
        private Set<String> _pathIncludes;
        private Path _dir;
        private String _ns;

        public Job(Path dir, String ns, boolean isParentNS, Collection<String> includes, Collection<String> excludes) {
            _dir = dir == null ? null : dir.toAbsolutePath();
            if (isParentNS) {
                _ns = PathUtils.join(ns, _dir.getFileName().toString());
            } else {
                _ns = ns;
            }
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
            _dir = Paths.get(je.value("directory")).toAbsolutePath();
            boolean isParentNS = je.booleanValue("namespace/@parent", true);
            if (isParentNS) {
                String ns = _dir.getFileName().toString();
                _ns = PathUtils.join(je.value("namespace"), ns);
            } else {
                _ns = je.value("namespace");
            }
            if (je.elementExists("include")) {
                _pathIncludes = new LinkedHashSet<String>();
                _pathIncludes.addAll(je.values("include"));
            }
            if (je.elementExists("exclude")) {
                _pathExcludes = new LinkedHashSet<String>();
                _pathExcludes.addAll(je.values("exclude"));
            }
        }

        public Path directory() {
            return _dir;
        }

        public String namespace() {
            return _ns;
        }

        public Set<String> excludes() {
            return _pathExcludes != null ? Collections.unmodifiableSet(_pathExcludes) : null;
        }

        public Set<String> includes() {
            return _pathIncludes != null ? Collections.unmodifiableSet(_pathIncludes) : null;
        }

        public boolean matchPath(Path path) {
            return matchPath(path.toAbsolutePath().toString());
        }

        public boolean matchPath(File path) {
            return matchPath(path.getAbsolutePath());
        }

        public boolean matchPath(String path) {
            if (!PathUtils.isOrIsDescendant(path, _dir.toString())) {
                return false;
            }
            if (_pathIncludes != null) {
                for (String include : _pathIncludes) {
                    if (!path.matches(include)) {
                        return false;
                    }
                }
            }
            if (_pathExcludes != null) {
                for (String exclude : _pathExcludes) {
                    if (path.matches(exclude)) {
                        return false;
                    }
                }
            }
            return true;
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
    private boolean _watchDaemon = false;
    private boolean _csumCheck = false;
    private boolean _excludeEmptyFolder = false;
    private Path _logDirectory = MFSync.DEFAULT_LOG_DIR;
    private Collection<String> _emailAddresses = null;

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
        _watchDaemon = se.booleanValue("settings/watchDaemon", false);
        _csumCheck = se.booleanValue("settings/csumCheck", false);
        _excludeEmptyFolder = se.booleanValue("settings/excludeEmptyFolder", false);
        _logDirectory = Paths.get(se.stringValue("settings/logDirectory", System.getProperty("user.dir")));
        _emailAddresses = se.values("settings/notification/email");
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

    public MFSyncSettings addJob(Path directory, String namespace, boolean isParentNS) {
        return addJob(directory, namespace, isParentNS, null, null);
    }

    public MFSyncSettings addJob(Path directory, String namespace, boolean isParentNS, Collection<String> includes,
            Collection<String> excludes) {
        return addJob(new Job(directory, namespace, isParentNS, includes, excludes));
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
        _watchDaemon = watchDaemon;
        return this;
    }

    public boolean watchDaemon() {
        return _watchDaemon;
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
            if (!AssetNamespaceUtils.assetNamespaceExists(session, job.parentNamespace())) {
                throw new IllegalArgumentException(
                        "Destination (parent) namespace: '" + job.parentNamespace() + "' does not exist.");
            }
        }
        if (_watchDaemon) {
            for (Job job : _jobs) {
                if (PathUtils.isOrIsDescendant(_logDirectory, job.directory())) {
                    throw new IllegalArgumentException("log directory: '" + _logDirectory
                            + "' is contained by one of the job source directory. This will cause uploading log file infinitely. Modify properties/sync/logDirectory value in the config file or specify --log.dir argument to point to a different location to resolve the issue.");
                }
            }
        }
    }

    public List<Job> jobsMatchPath(Path path) {
        return jobsMatchPath(path.toAbsolutePath().toString());
    }

    public List<Job> jobsMatchPath(String path) {
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
        settings.setWatchDaemon(_watchDaemon);
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
        ps.println("    daemon: " + _watchDaemon);
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

}
