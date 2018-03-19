# mf-sync-client
A client application to upload local files to Mediaflux server. It can also be configured to run as a background daemon to scan for local file changes periodically and upload to remote Mediaflux server.

## 1. Installation

* [Java 8](https://java.com/en/download/) must be installed.
* Download latest release from: [https://github.com/UoM-ResPlat-DevOps/mf-sync-client/releases](https://github.com/UoM-ResPlat-DevOps/mf-sync-client/releases)
* Unzip it to the install directory:
  * **`cd opt; sudo unzip ~/Downloads/mf-sync-client-x.y.z.zip`**
  * **`sudo ln -s /opt/mf-sync-client-x.y.z /opt/mf-sync-client`**
* (Optionally, if you are using Bash shell) You can edit your **`~/.bashrc`** file to add **mf-sync** to your environment variable.
  * **`echo "export PATH=/opt/mf-sync-client:$PATH" >> ~/.bashrc`**

## 2. Usage

The command `mf-sync` is be executed in a Unix terminal (or Windows Command Prompt). 

### 2.1. Command arguments

The arguments of **mf-sync** are described below:
```
USAGE:
    mf-sync [options] [<src-directory> <dst-asset-namespace>]

DESCRIPTION:
    mf-sync is a tool to upload files and directories to Mediaflux server. It supports also daemon mode, which runs in background to scan for local changes and upload to Mediaflux.

OPTIONS:
    --help                               Display help information.
    --mf.host <host>                     The Mediaflux server host.
    --mf.port <port>                     The Mediaflux server port.
    --mf.transport <transport>           The Mediaflux server transport, can be http, https or tcp/ip.
    --mf.auth <domain,user,password>     The Mediaflux user authentication deatils.
    --mf.token <token>                   The Mediaflux secure identity token.
    --conf <config-file>                 The configuration file. Defaults to '~/.mediaflux/mf-sync.properties'
    --number-of-workers <n>              Number of worker threads to upload the files. If not specified, defaults to 1.
    --max-checkers <n>                   Maximum number of checker threads to compare local files with Mediaflux assets. If not specified, defaults to 1.
    --check-batch-size <n>               Batch size for comparing files with Mediaflux assets. Defaults to 100, which checks 100 files within single service request.
    --exclude-empty-folder               Exclude empty folders. In other words, upload files only.
    --csum-check                         Validate CRC32 checksum after uploading. It will slow down the proccess.
    --notification-emails  <a@b.org>     The (comma-separated) email addresses for notification recipients.
    --log-dir <logging-directory>        The directory for log files. If not specified, defaults to current work directory.
    --daemon                             Runs as a daemon to periodically scan the changes and upload.
    --daemon-port                        The listening port of the daemon. Defaults to 9761. It accepts connection from localhost only. It responds to 'status' and 'stop' requests. If 'status', it responds with the current application status; If 'stop', it will shutdown the daemon and exit the application. You can use netcat to send command to the daemon listener port, e.g 'echo status | nc localhost 9761' or to stop the daemon 'echo stop | nc localhost 9761'
    --daemon-scan-interval               The time interval in milliseconds between scans. Defaults to 60000 (1 minute). It only starts scanning when the daemon is idle. In other words, it skips scans if previous scan or upload has not completed. 

POSITIONAL ARGUMENTS:
    <src-directory>                      The source directory.
    <dst-asset-namespace>                The destination (parent) asset namespace.
    Note: to specify multiple 'source directory' and 'destination asset namespace' pairs, you need to add them to the configuration file as upload jobs.
```

### 2.2. Configuration file

You can specify the arguments in the command line or specify the corresponding properties in the configuration file. The **mf-sync** loads the configuration file in the following order:
  1) try loading from default location: _**~/.mediaflux/mf-sync-properties.xml**_;
  2) if **--conf** argument specified, load configuration from the file (and it will overwrite the values loaded previously);
  3) if specified in the command line, it will overwrite the values loaded previously;

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

