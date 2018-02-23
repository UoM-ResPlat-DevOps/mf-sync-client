@echo off

cmd /k java -cp "%~dp0\mf-sync-client.jar" resplat.mf.client.sync.MFSyncCLI %*
