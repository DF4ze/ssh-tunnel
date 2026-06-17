@echo off
cd /d "D:\Installs\Ssh-tunnel"

:loop
java -jar "D:\Installs\Ssh-tunnel\ssh-tunnel-gateway.jar"

if exist "D:\Installs\Ssh-tunnel\restart.trigger" (
    del "D:\Installs\Ssh-tunnel\restart.trigger"
    goto loop
)
