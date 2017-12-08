# mf-sync-client
A client application to upload local files to Mediaflux server.

## I. Installation

  1. Download latest release from: [https://github.com/UoM-ResPlat-DevOps/mf-sync-client/releases](https://github.com/UoM-ResPlat-DevOps/mf-sync-client/releases)
  2. Unzip it to the destination directory:
    * ```cd opt; sudo unzip ~/Downloads/mf-sync-client-x.x.x.zip```

## II. Tools

### mf-sync

```
USAGE:
    mf-sync [options] <src-directory> <dst-asset-namespace>

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
    --sync.local.deletion                Synchronize local deletions.
    --threads <n>                        Number of worker threads to upload the files. Defaults to 1.
    --log.dir <logging-directory>        The directory to save the logs. Defaults to current work directory.
    --conf <config-file>                 The configuration file. Defaults to ~/.mediaflux/mf-sync.properties Note: settings in the configuration file can be overridden by the command arguments.

POSITIONAL ARGUMENTS:
    <src-directory>                      The local directory to upload/synchronize from.
    <dst-asset-namespace>                The remote (parent) destination asset namespace to upload/synchronize to.
```

### mf-sync-daemon

**mf-sync-daemon** is a wrapper shell script to run the **mf-sync** tool as daemon to monitor the changes in the specified directory. It is equivalent to:

```
mf-sync --watch [options] <src-directory> <dst-asset-namespace>
```

Additionally, it returns the **pid** (process id). To stop the daemon by **pid**:

```
kill -15 <pid>
```

## III. Configuration

  * The default config file location is **$HOME/.mediaflux/mf-sync.properties** (or **%USERPROFILE%/.mediaflux/mf-sync.properties** )
    * On Windows you can create the directory with:
      * ```mkdir %USERPROFILE%/.mediaflux```
  * You can also specify --conf <config-file> to override it.
  * If config file does not exist and no **--conf** is specified, the required arguments must be specified in the command line.
  * So the process is: 
    1. load config file at the default location;
    2. load config file specified after --conf;
    3. load settings in the command arguments;
 
 ### Sample config file
 ```shell
 # Mediaflux server host
mf.host=mediaflux.your-organization.org

# Mediaflux server port
mf.port=443

# Mediaflux server transport protocol, can be http, https or tcp/ip.
mf.transport=https

# Mediaflux user credentials. If not specified, mf.token must be present.
#mf.auth=domain,user,password

# Mediaflux secure identity token
mf.token=XXXXXXXXXXXXXXXXX

# Number of worker threads
threads=1

# Watch the changes of the specified (Run as daemon)
watch=true

# Source directory
directory=/path/to/src-directory

# Remote (parent) destination namespace
# Results in /path/to/dst-namespace/src-directory being created
namespace=/path/to/dst-namespace

# Directory to save the logs, defaults to currrent working directory.
#log.dir=/path/to/logs/

 ```

## IV. Limitations

### 1. (In daemon/watch mode) namespaces contains soft-destroyed assets cannot be destroyed
  * When running as daemon by specifying --watch argument, local file deletions will NOT be synchronised unless --sync.local.deletion is enabled;
  * If --watch and --sync.local.deletion are enabled, when a local file is deleted, the corresponding remote asset will be **soft-destroyed**. When a local directory is deleted, all the assets in the corresponding remote namespace are **soft-destroyed**. But we cannot hide the namespace that contains only soft-destroyed assets.

### 2. (In daemon/watch mode) renaming/moving a directory causes file uploads
  * When monitoring the changes in the local directory with --watch argument, **DIRECTORY_RENAME** event cannot be detected. This is due to the limitation of Java File Watcher Service. Instead, **DIRECTORY_DELETE** and **DIRECTORY_CREATE** events are triggered when renaming/moving a directory. This will cause the **NAMESPACE_DESTROY** and **NAMESPACE_CREATE** action on Mediaflux server. Since the assets are **soft** destroyed, the above (two) actions will cause reuploading the directory to a different location and double the storage usage.
