#!/bin/bash
# Prepara el entorno de Claude en la nube para compilar y probar SocialHub (se pierde al reiniciar el contenedor).
# Uso (como root): bash scripts/entorno/preparar.sh
#  - Java 25 (Temurin) en /opt/jdk, Maven en /opt/maven (si faltan, los descarga).
#  - Usuario «claude»: las pruebas que arrancan PostgreSQL no pueden ejecutarse como root.
#  - Envoltorios /opt/jdk/mvnu (Maven como «claude» con el proxy) y /opt/jdk/npmp (npm con el proxy).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../.." && pwd)

id claude >/dev/null 2>&1 || useradd -m claude
mkdir -p /opt/jdk
if ! ls -d /opt/jdk/jdk-25* >/dev/null 2>&1; then
  curl -sSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" -o /tmp/jdk.tgz
  tar -xzf /tmp/jdk.tgz -C /opt/jdk && rm /tmp/jdk.tgz
fi
jdk=$(ls -d /opt/jdk/jdk-25* | head -1)
echo "export JAVA_HOME=$jdk; export PATH=$jdk/bin:\$PATH" > /opt/jdk/env.sh
if [ ! -x /opt/maven/bin/mvn ]; then
  v=3.9.11
  curl -sSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$v/apache-maven-$v-bin.tar.gz" -o /tmp/mvn.tgz
  mkdir -p /opt/maven && tar -xzf /tmp/mvn.tgz -C /opt/maven --strip-components=1 && rm /tmp/mvn.tgz
fi
mkdir -p /home/claude/.m2
[ -f /home/claude/.m2/settings.xml ] || echo '<settings><localRepository>/home/claude/.m2/repository</localRepository><proxies><proxy><id>agent</id><active>true</active><protocol>https</protocol><host>127.0.0.1</host><port>0</port><nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy></proxies></settings>' > /home/claude/.m2/settings.xml
install -m 755 "$here/mvnu" /opt/jdk/mvnu
install -m 755 "$here/npmp" /opt/jdk/npmp
chown -R claude /home/claude "$repo"
echo "Listo: $jdk · $(/opt/maven/bin/mvn -v 2>/dev/null | head -1)"
