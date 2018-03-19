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

Alternative to command line arguments, you can also specify the corresponding properties in a XML configuration file. The advantiages of using configuration file instead of command line arguments are:
  * you can specify multiple upload jobs (src-dir to dst-namespace pairs) in the configuration file;
  * you can specify (inclusive or exclusive) filters for each upload job to select files in the source directory in the configuration file;

The default config file location is **$HOME/.mediaflux/mf-sync-properties.xml** (or **%USERPROFILE%/.mediaflux/mf-sync-properties.xml** on Windows)
  * On Windows you can create the directory in **Command Prompt** with following command:
    * ```md %USERPROFILE%/.mediaflux```

The **mf-sync** loads the configuration file in the following order:
  1) try loading the configration file from its default location: _**~/.mediaflux/mf-sync-properties.xml**_;
  2) if **--conf** argument is specified, load configuration from the file (and it will overwrite the values loaded previously);
  3) if specified in the command line, it will overwrite the values loaded previously;

### **[Sample XML configuration file](https://github.com/UoM-ResPlat-DevOps/mf-sync-client/blob/master/src/main/config/mf-sync-properties.sample.xml) (with property descriptions as comments)**
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

## 3. Examples:

### 3.1. Upload single directory

To upload directory **/data/src-dir1** to asset namespace **/projects/cryo-em/proj-abc-1128.4.66**
  * **option 1**:  using configuration file:
```xml
<sync>
    <job type="upload">
        <directory>/data/src-dir1</directory>
        <namespace parent="true">/projects/cryo-em/proj-abc-1128.4.66</namespace>
    </job>
</sync>
```
  * **option 2**: using command arguments:
    * **`mf-sync /data/src-dir1 /projects/cryo-em/proj-abc-1128.4.66`**

### 3.2. Run **mf-sync** as daemon:

  * **option 1**: using configuration file:
```xml
<sync>
    <settings>
        <daemon enabled="true">
            <listenerPort>9761</listenerPort>
            <scanInterval>60000</scanInterval>
        </daemon>
    </settings>
</sync>
```
  * **option 2**: using command arguments:
    * **`mf-sync --daemon --daemon-port 9761 --daemon-scan-interval 60000`**

### 3.3. Upload multiple directories

You can only achieve this by using the configuration file.
```xml
<sync>
    <job type="upload">
        <directory>/data/src-dir1</directory>
        <namespace parent="true">/projects/cryo-em/proj-abc-1128.4.66</namespace>
    </job>
    <job type="upload">
        <directory>/data/src-dir2</directory>
        <namespace parent="true">/projects/cryo-em/proj-abc-1128.4.67</namespace>
    </job>
</sync>
```

### 3.4. Upload matching files/directories by specifying pattern selectors/filters

  * Upload all (direct) sub-directories with name starts with _**wilson**_; and exclude all files with name _**.DS_Store**_
```xml
<sync>
    <job type="upload">
        <directory>/data/raw-data</directory>
	<include>wilson*/**</include>
	<exclude>**/.DS_Store</exclude>
        <namespace parent="true">/projects/cryo-em/proj-abc-1128.4.68</namespace>
    </job>
</sync>
```

### 3.5. Configure to send notification emails (non-daemon mode)
```xml
<sync>
    <settings>
        <notification>
            <email>admin1@your-domain.org</email>
            <email>admin2@your-domain.org</email>
	</notification>
    </settings>
</sync>
```

### 3.6. In daemon mode, check the status of the daemon

When running **mf-sync** in daemon mode, you can run netcat to send command to the daemon listener port to check the status of the daemon:
  * **`echo status | nc localhost 9761`**

In the above example, 9761 is the daemon listener port. The **mf-sync** restrict to listen to only localhost. You cannot access the port remotely.

 


