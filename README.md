# mf-sync-client
A client application to upload local files to Mediaflux server. It can also be configured to run as a background daemon to scan for local file changes periodically and upload to remote Mediaflux server.

## 1. Installation

* a. [Java 8](https://java.com/en/download/) must be installed.
* b. Download latest release from: [https://github.com/UoM-ResPlat-DevOps/mf-sync-client/releases](https://github.com/UoM-ResPlat-DevOps/mf-sync-client/releases)
* c. Unzip it to the install directory:
  * **`cd opt; sudo unzip ~/Downloads/mf-sync-client-x.y.z.zip`**
  * **`sudo ln -s /opt/mf-sync-client-x.y.z /opt/mf-sync-client`**
* d. (Optionally, if you are using Bash shell) You can edit your **`~/.bashrc`** file to add **mf-sync** to your environment variable.
  * **`echo "export PATH=/opt/mf-sync-client:$PATH" >> ~/.bashrc`**

## 2. Usage

The command `mf-sync` is be executed in a Unix terminal (or Windows Command Prompt window). The command usage is described below:

```
USAGE:
    mf-sync [options] [<src-directory> <dst-asset-namespace>]

DESCRIPTION:
    mf-sync is a tool to upload local files from the specified directory to remote Mediaflux asset namespace. 
It can also run in background as a daemon to scan for local changes in the directory and upload to the asset namespace.

OPTIONS:
    --help                               Display help information.
    --conf <config-file>                 The configuration file. Note: this argument is optional. If not specified, defaults to ~/.mediaflux/mf-sync-properties.xml (or %USERPROFILE%\.mediaflux\mf-sync-properties.xml on Windows).

POSITIONAL ARGUMENTS:
    <src-directory>                      The source directory.
    <dst-asset-namespace>                The destination asset namespace.
Note: If source directory and destination namespace are not specified in command line. They must be specified in the configuration file.

```

### **Examples:**

1. Start **`mf-sync`** and load configuration file from specified location.
  * **`mf-sync --conf ~/Documents/mf-sync-properties.xml`**
2. Start **`mf-sync`** (load configuration from default location **`~/.mediaflux/mf-sync-properties.xml`**

### mf-sync

```
USAGE:
    mf-sync [options] [src-directory dst-asset-namespace]

DESCRIPTION:
    mf-sync is a tool to upload local files from the specified directory to remote Mediaflux asset namespace. 
 It can also run as a daemon to monitor the local changes in the directory and synchronize to the asset namespace.

OPTIONS:
    --help                               Display help information.
    --mf.host <host>                     The Mediaflux server host.
    --mf.port <port>                     The Mediaflux server port.
    --mf.transport <transport>           The Mediaflux server transport, can be http, https or tcp/ip.
    --mf.auth <domain,user,password>     The Mediaflux user authentication deatils.
    --mf.token <token>                   The Mediaflux secure identity token.
    --mf.sid <sid>                       The Mediaflux session id.
    --daemon                             Start a daemon to watch the changes in the specified directory.
    --csum-check                         Validate CRC32 checksum. It will slow down the upload process.
    --exclude-empty-folder               Exclude empty folders.
    --number-of-workers <n>              Number of worker threads to upload the files. Defaults to 1.
    --log-dir <logging-directory>        The directory to save the logs. Defaults to current work directory.
    --conf <config-file>                 The configuration file. Defaults to ~/.mediaflux/mf-sync.properties.
Note: settings in the configuration file can be overridden by the command arguments.

POSITIONAL ARGUMENTS:
    <src-directory>                      The local sourcedirectory to upload/synchronize from.
    <dst-asset-namespace>                The remote (parent) destination asset namespace to upload/synchronize to.
```

### mf-sync-daemon

**mf-sync-daemon** is a wrapper shell script to run the **mf-sync** tool as daemon to monitor the changes in the specified directory. It is equivalent to:

```
mf-sync --daemon [options] <src-directory> <dst-asset-namespace>
```

Additionally, it returns the **pid** (process id). To stop the daemon by **pid**:

```
kill -15 <pid>
```

## III. Configuration

  * The default config file location is **$HOME/.mediaflux/mf-sync-properties.xml** (or **%USERPROFILE%/.mediaflux/mf-sync-properties.xml** on Windows)
    * On Windows you can create the directory in **Command Prompt** with following command:
      * ```mkdir %USERPROFILE%/.mediaflux```
  * You can also specify --conf <config-file> to override it.
  * If config file does not exist and no **--conf** is specified, the required arguments must be specified in the command line.
  * So the process is: 
    1. load config file at the default location;
    2. load config file specified after --conf;
    3. load settings in the command arguments;
 
 ### Sample config file
 ```xml
<?xml version="1.0"?>
<properties>
	<server>
		<!-- Mediaflux server host -->
		<host>mediaflux.vicnode.org.au</host>
		<!-- Mediaflux server port -->
		<port>443</port>
		<!-- Mediaflux server transport. https, http or tcp/ip -->
		<protocol>https</protocol>
		<session>
			<!-- Retry times on Mediaflux connection failure -->
			<connectRetryTimes>1</connectRetryTimes>
			<!-- Time interval (in milliseconds) between retries -->
			<connectRetryInterval>1000</connectRetryInterval>
			<!-- Retry times on service execution error -->
			<executeRetryTimes>1</executeRetryTimes>
			<!-- Time interval (in milliseconds) between retries -->
			<executeRetryInterval>1000</executeRetryInterval>
		</session>
	</server>
	<credential>
		<!-- Application name. Can be used as application key for secure identity 
			token -->
		<app>mf-sync</app>
		<!-- Mediaflux user's authentication domain -->
		<domain>system</domain>
		<!-- Mediaflux username -->
		<user>wilson</user>
		<!-- Mediaflux user's password -->
		<password>change_me</password>
		<!-- Mediaflux secure identity token -->
		<token>xxxyyyzzz</token>
	</credential>
	<sync>
		<settings>
			<!-- Number of workers/threads to upload data concurrently -->
			<numberOfWorkers>8</numberOfWorkers>
			<!-- Number of checkers/threads to compare local files with remote assets -->
			<maxNumberOfCheckers>4</maxNumberOfCheckers>
			<!-- Batch size for checking files with remote assets. Set to 1 will check 
				files one by one, which will slow down significantly when there are large 
				number of small files. -->
			<checkBatchSize>100</checkBatchSize>
			<!-- Compare CRC32 checksum after uploading -->
			<csumCheck>true</csumCheck>
			<!-- Running a daemon in background to scan for local file system changes 
				periodically. -->
			<daemon enabled="true">
				<listenerPort>9761</listenerPort>
				<!-- Time interval (in milliseconds) between scans -->
				<scanInterval>60000</scanInterval>
			</daemon>
			<!-- Log directory location -->
			<logDirectory>/tmp</logDirectory>
			<!-- Exclude empty directories for uploading -->
			<excludeEmptyFolder>true</excludeEmptyFolder>
			<!-- Send notifications when jobs complete (Non-daemon mode only) -->
			<notification>
				<!-- The email recipients. Can be multiple. -->
				<email>wliu5@unimelb.edu.au</email>
			</notification>
		</settings>
		<!-- Upload jobs -->
		<job type="upload">
			<!-- source directory -->
			<directory>/path/to/src-directory1</directory>
			<!-- destination asset namespace. Set parent attribute to true if the 
				namespace should be a parent namespace. -->
			<project parent="true">51</project>
			<!-- inclusive filter. The filter below select all sub-directories' name 
				start with wilson under the source directory, and all their descendants. -->
			<include>wilson*/**</include>
			<!-- exclusive filter. The filter below excludes all files with name: 
				.DS_store -->
			<exclude>**/.DS_Store</exclude>
		</job>
		<job type="upload">
			<!-- source directory -->
			<directory>/path/to/src-directory2</directory>
			<!-- destination asset namespace -->
			<namespace parent="false">/path/to/dst-namespace2</namespace>
			<!-- The filter below excludes all .class files. -->
			<exclude>**/*.class</exclude>
		</job>
	</sync>
</properties>
 ```

## IV. Limitations

### 1. (In daemon mode) namespaces contains soft-destroyed assets cannot be destroyed
  * When running as daemon by specifying --watch argument, local file deletions will NOT be synchronised unless --sync.local.deletion is enabled;
  * If daemon mode is enabled, when a local file is deleted, the corresponding remote asset will be **soft-destroyed**. When a local directory is deleted, all the assets in the corresponding remote namespace are **soft-destroyed**. But we cannot hide the namespace that contains only soft-destroyed assets.

### 2. (In daemon mode) renaming/moving a directory causes file uploads
  * When monitoring the changes in the local directory with --watch argument, **DIRECTORY_RENAME** event cannot be detected. This is due to the limitation of Java File Watcher Service. Instead, **DIRECTORY_DELETE** and **DIRECTORY_CREATE** events are triggered when renaming/moving a directory. This will cause the **NAMESPACE_DESTROY** and **NAMESPACE_CREATE** action on Mediaflux server. Since the assets are **soft** destroyed, the above (two) actions will cause reuploading the directory to a different location and double the storage usage.
