#!/usr/bin/env bash
set -euo pipefail

# Nixpacks exécute chaque entrée de cmds dans un RUN Docker distinct :
# JAVA_HOME doit être défini dans le même shell que mvn.
JAVA_HOME="$(ls -d /nix/store/*-openjdk-21* 2>/dev/null | head -1)"
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

echo "JAVA_HOME=${JAVA_HOME}"
java -version
javac --version
mvn -version

mvn -DoutputFile=target/mvn-dependency-list.log -B -DskipTests clean dependency:list install
