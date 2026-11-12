@echo off
REM ============================================================
REM  Print LAN URLs for phone access (PC IP changes with DHCP).
REM  ASCII-only on purpose: Chinese text breaks under cmd's code page.
REM  Skips loopback / link-local / virtual adapters (vEthernet, VMware...)
REM ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.InterfaceAlias -notlike 'vEthernet*' -and $_.InterfaceAlias -notlike '*VM*' -and $_.InterfaceAlias -notlike '*Bluetooth*' } | ForEach-Object { '   http://' + $_.IPAddress + ':5173' }"
echo.
echo   Phone must be on the SAME network as the PC.
echo   Use http:// (NOT https://). If it hangs, re-run this file for a fresh IP.
echo.
pause
