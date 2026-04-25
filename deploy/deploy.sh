#!/bin/bash
# Deploy script for Thales Transparent Load Balancer
# Run from project root after: mvn clean package -DskipTests

set -e

BIN_DIR=/data1/xenticate/hsm-lb/bin
CONFIG_DIR=/data1/xenticate/hsm-lb/config
EZNET_DIR=/data1/xenticate/hsm-lb/eznet-inbound
LOG_DIR=/var/log/xenticate
SUPERVISOR_CONF=/etc/supervisor/conf.d/thales-lb.conf
EZNET_WAR=/home/xenticate/bank_deploy/bin/eznet/eznet-tcp2jms.war

echo "=== Thales Transparent LB Deploy ==="

echo "Xenticate@2285" | sudo -S mkdir -p "$BIN_DIR" "$CONFIG_DIR" "$EZNET_DIR/config" "$LOG_DIR"

# Copy LB jar
echo "Copying LB jar..."
echo "Xenticate@2285" | sudo -S cp target/thales-transparent-lb.jar "$BIN_DIR/"

# Copy EzNet war
echo "Copying EzNet war..."
echo "Xenticate@2285" | sudo -S cp "$EZNET_WAR" "$BIN_DIR/"

# Copy configs
echo "Copying configs..."
echo "Xenticate@2285" | sudo -S cp deploy/eznet-inbound/config/application.properties "$EZNET_DIR/config/"
echo "Xenticate@2285" | sudo -S cp src/main/resources/application.properties "$CONFIG_DIR/"

# Copy supervisor conf
echo "Copying supervisor conf..."
echo "Xenticate@2285" | sudo -S cp deploy/supervisor/thales-lb.conf "$SUPERVISOR_CONF"

# Set ownership
echo "Xenticate@2285" | sudo -S chown -R xenticate:xenticate /data1/xenticate/hsm-lb

# Reload supervisor
echo "Reloading supervisord..."
echo "Xenticate@2285" | sudo -S supervisorctl reread
echo "Xenticate@2285" | sudo -S supervisorctl update

echo ""
echo "=== Deploy complete. Check status: ==="
echo "Xenticate@2285" | sudo -S supervisorctl status thales-lb thales-lb-inbound
