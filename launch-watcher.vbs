Set WshShell = CreateObject("WScript.Shell")
WshShell.Run "powershell.exe -WindowStyle Hidden -ExecutionPolicy Bypass -File ""D:\Installs\Ssh-tunnel\gateway-watcher.ps1""", 0
