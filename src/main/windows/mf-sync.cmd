@echo off

cmd /k java -cp "%~dp0\mf-sync-client.jar" unimelb.mf.client.sync.MFSyncCLI %*
