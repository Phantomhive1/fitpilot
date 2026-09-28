# ============================================================
# 一键部署脚本（非 Docker 模式，传统方式）
# 用法（在服务器上）：
#   sudo bash deploy/scripts/deploy.sh
#
# 假定服务器：Ubuntu 22.04，已装 Java 17 + MySQL 8 + Nginx
# ============================================================
#!/usr/bin/env bash
set -euo pipefail

APP_DIR=${APP_DIR:-/opt/fitpilot}
SERVICE_NAME=fitpilot
JAR_NAME=fitpilot-backend.jar

echo "==> 1. 创建应用目录"
mkdir -p "$APP_DIR"
mkdir -p /var/log/fitpilot

echo "==> 2. 拷贝 jar（需先在本地 mvn package 后上传）"
if [[ ! -f "$APP_DIR/$JAR_NAME" ]]; then
    echo "✗ 未找到 $APP_DIR/$JAR_NAME，请先本地打包上传："
    echo "    mvn -pl fitpilot-backend clean package -DskipTests"
    echo "    scp fitpilot-backend/target/fitpilot-backend-*.jar root@server:$APP_DIR/$JAR_NAME"
    exit 1
fi

echo "==> 3. 安装 systemd unit"
cp deploy/fitpilot.service /etc/systemd/system/$SERVICE_NAME.service
systemctl daemon-reload
systemctl enable --now $SERVICE_NAME

echo "==> 4. 等待启动"
sleep 5
systemctl status $SERVICE_NAME --no-pager || true

echo ""
echo "✓ 部署完成！"
echo "  - 查看日志：journalctl -u $SERVICE_NAME -f"
echo "  - 健康检查：curl http://127.0.0.1:8080/api/requirements/latest"
echo "  - 配 nginx：cp deploy/nginx/fitpilot.conf /etc/nginx/conf.d/"