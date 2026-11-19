#!/usr/bin/env bash
# Compila (sempre, para pegar qualquer alteração) e sobe a aplicação.
set -e
cd "$(dirname "$0")"
echo "Compilando o projeto..."
mvn -q clean package
java -jar target/hotel-dashboard.jar
