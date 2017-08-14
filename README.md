# mf-sync-client
A client application to upload local files to Mediaflux server


## II. Commands

### mf-sync

```
USAGE:
    mf-sync [options] <directory> <asset-namespace>

DESCRIPTION:
    mf-sync is file uploading tool to upload local files from the specified directory to remote Mediaflux asset namespace. It can also run as a daemon to monitor the local changes in the directory and synchronize to the asset namespace.

OPTIONS:
    --help                               Display help information.
    --mf.host <host>                     The Mediaflux server host.
    --mf.port <port>                     The Mediaflux server port.
    --mf.transport <transport>           The Mediaflux server transport, can be http, https or tcp/ip.
    --mf.auth <domain,user,password>     The Mediaflux user authentication deatils.
    --mf.token <token>                   The Mediaflux secure identity token.
    --mf.sid <sid>                       The Mediaflux session id.
    --watch                              Start a daemon to watch the changes in the specified directory.
    --create.directory                   Create directory if it does not exist.
    --create.namespace                   Create asset namespace if it does not exist.
    --threads <n>                        Number of worker threads to upload the files. Defaults to 1.
    --log.dir <logging-directory>        The directory to save the logs. Defaults to current work directory.
    --conf <config-file>                 The configuration file. Defaults to ~/.mediaflux/mf-sync.properties Note: settings in the configuration file can be overridden by the command arguments.

POSITIONAL ARGUMENTS:
    <directory>                          The local directory to upload/synchronize from.
    <asset-namespace>                    The remote asset namespace to upload/synchronize to.
```

### mf-sync-daemon

mf-sync-daemon is a wrapper shell script to run the mf-sync tool as daemon to monitor the changes in the specified directory. It is equivalent to:

```
mf-sync --watch [options] <directory> <asset-namespace>
```

## Configuration

### config file
