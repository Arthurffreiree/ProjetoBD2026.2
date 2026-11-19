@echo off
setlocal
rem Compila (sempre, para pegar qualquer alteração) e sobe a aplicação.
echo Compilando o projeto...
call mvn -q clean package
if errorlevel 1 exit /b 1
java -jar target\hotel-dashboard.jar
