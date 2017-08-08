@echo off

cmd /k java -cp "%~dp0\mf-sync-client.jar" vicnode.mf.client.MFSyncCLI %*
