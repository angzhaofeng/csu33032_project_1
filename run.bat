@echo off
setlocal

pushd "%~dp0"

if not exist "target\web-proxy-server-1.0-SNAPSHOT.jar" (
    echo JAR not found: target\web-proxy-server-1.0-SNAPSHOT.jar
    echo Build first with: mvn clean package
    popd
    exit /b 1
)

java -cp target/web-proxy-server-1.0-SNAPSHOT.jar com.proxy.ProxyServer
set EXIT_CODE=%ERRORLEVEL%

popd
exit /b %EXIT_CODE%
