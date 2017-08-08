package vicnode.mf.client.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import arc.xml.XmlDoc;
import arc.xml.XmlWriter;

public class PosixAttributes {

    public static final String DOC_TYPE = "arc.posix.attributes";

    private Long _uid;
    private Long _gid;
    private Integer _mode;
    private Long _ctime;
    private Long _mtime;
    private String _symlink;
    private String _owner;
    private String _group;
    private List<AclEntry> _acl;

    public PosixAttributes(Map<String, Object> unixFileAttributes, String symlink, List<AclEntry> acl) {
        Object value = unixFileAttributes.get("uid");
        _uid = value == null ? null : ((Integer) value).longValue();
        value = unixFileAttributes.get("gid");
        _gid = value == null ? null : ((Integer) value).longValue();
        value = unixFileAttributes.get("mode");
        _mode = value == null ? null : (((Integer) value) & 07777);
        value = unixFileAttributes.get("creationTime");
        _ctime = value == null ? null : ((FileTime) value).toMillis();
        value = unixFileAttributes.get("lastModifiedTime");
        _mtime = value == null ? null : ((FileTime) value).toMillis();
        value = unixFileAttributes.get("owner");
        _owner = value == null ? null : value.toString();
        value = unixFileAttributes.get("group");
        _group = value == null ? null : value.toString();
        _symlink = symlink;
        _acl = acl;
    }

    public PosixAttributes(XmlDoc.Element xe) throws Throwable {
        _uid = xe.longValue("uid", null);
        _gid = xe.longValue("gid", null);
        _mode = xe.intValue("mode", null);
        _ctime = xe.longValue("ctime", null);
        _mtime = xe.longValue("mtime", null);
        _owner = xe.value("owner");
        _group = xe.value("group");
        _symlink = xe.value("symlink");
        if (xe.elementExists("acl")) {
            List<XmlDoc.Element> aes = xe.elements("acl");
            _acl = new ArrayList<AclEntry>(aes.size());
            for (XmlDoc.Element ae : aes) {
                _acl.add(new AclEntry(ae));
            }
        }
    }

    public Long uid() {
        return _uid;
    }

    public boolean uidEquals(PosixAttributes attr) {
        return (_uid == null && attr.uid() == null) || (_uid != null && attr.uid() != null && _uid.equals(attr.uid()));
    }

    public Long gid() {
        return _gid;
    }

    public boolean gidEquals(PosixAttributes attr) {
        return (_gid == null && attr.gid() == null) || (_gid != null && attr.gid() != null && _gid.equals(attr.gid()));
    }

    public Integer mode() {
        return _mode;
    }

    public boolean modeEquals(PosixAttributes attr) {
        return (_mode == null && attr.mode() == null)
                || (_mode != null && attr.mode() != null && _mode.equals(attr.mode()));
    }

    public Long ctime() {
        return _ctime;
    }

    public boolean ctimeEquals(PosixAttributes attr) {
        return (_ctime == null && attr.ctime() == null)
                || (_ctime != null && attr.ctime() != null && _ctime.equals(attr.ctime()));
    }

    public boolean ctimeGreaterThan(PosixAttributes attr) {
        return (_ctime != null && attr.ctime() == null)
                || (_ctime != null && attr.ctime() != null && _ctime > attr.ctime());
    }

    public boolean ctimeLessThan(PosixAttributes attr) {
        return (_ctime == null && attr.ctime() != null)
                || (_ctime != null && attr.ctime() != null && _ctime < attr.ctime());
    }

    public Long mtime() {
        return _mtime;
    }

    public boolean mtimeEquals(PosixAttributes attr) {
        return (_mtime == null && attr.mtime() == null)
                || (_mtime != null && attr.mtime() != null && _mtime.equals(attr.mtime()));
    }

    public boolean mtimeGreaterThan(PosixAttributes attr) {
        return (_mtime != null && attr.mtime() == null)
                || (_mtime != null && attr.mtime() != null && _mtime > attr.mtime());
    }

    public boolean mtimeLessThan(PosixAttributes attr) {
        return (_mtime == null && attr.mtime() != null)
                || (_mtime != null && attr.mtime() != null && _mtime < attr.mtime());
    }

    public String symlink() {
        return _symlink;
    }

    public String owner() {
        return _owner;
    }

    public String group() {
        return _group;
    }

    public List<AclEntry> acl() {
        if (_acl == null) {
            return null;
        }
        return Collections.unmodifiableList(_acl);
    }

    public void save(XmlWriter w) throws Throwable {
        w.push("arc.posix.attributes");
        if (_uid != null) {
            w.add("uid", _uid);
        }
        if (_gid != null) {
            w.add("gid", _gid);
        }
        if (_mode != null) {
            w.add("mode", _mode);
        }
        if (_ctime != null) {
            w.add("ctime", _ctime);
        }
        if (_mtime != null) {
            w.add("mtime", _mtime);
        }
        if (_owner != null) {
            w.add("owner", _owner);
        }
        if (_group != null) {
            w.add("group", _group);
        }
        if (_symlink != null) {
            w.add("symlink", _symlink);
        }
        if (_acl != null) {
            for (AclEntry e : _acl) {
                e.save(w);
            }
        }
        w.pop();
    }

    public static PosixAttributes read(File file) throws IOException {
        return read(file.toPath());
    }

    public static PosixAttributes read(Path path) throws IOException {
        Map<String, Object> unixFileAttributes = Files.readAttributes(path, "unix:*", LinkOption.NOFOLLOW_LINKS);
        String symlink = ((Boolean) unixFileAttributes.get("isSymbolicLink")) ? Files.readSymbolicLink(path).toString()
                : null;
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
        List<AclEntry> acl = view == null ? null : AclEntry.convertFrom(view.getAcl());
        return new PosixAttributes(unixFileAttributes, symlink, acl);
    }

    public static void save(Path path, XmlWriter w) throws Throwable {
        read(path).save(w);
    }

    public static void save(File file, XmlWriter w) throws Throwable {
        read(file).save(w);
    }

    public static void main(String[] args) throws IOException {
        PosixAttributes attrs = read(new File("/Users/wliu5/proxy"));
        System.out.println(attrs.symlink());
        System.out.println(new Date(attrs.ctime()));
    }

}
