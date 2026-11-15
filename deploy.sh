#!/usr/bin/env bash
# 服务器侧一键部署脚本，放在 /opt/hrsBlog 下与 compose.production.yaml 同级。
#
#   ./deploy.sh              # 拉取并部署 latest（日常更新）
#   ./deploy.sh <commit-sha> # 部署某个固定版本（回滚 / 精确发布）
#
# 依赖：docker、docker compose v2、curl、.env（含数据库与 JWT 等运行时变量）
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE_FILE="compose.production.yaml"
ENV_FILE=".env"

[ -f "$COMPOSE_FILE" ] || { echo "缺少 $COMPOSE_FILE" >&2; exit 1; }
[ -r "$ENV_FILE" ]     || { echo "缺少或不可读 $ENV_FILE" >&2; exit 1; }

# 博客静态产物由 CI 同步到 ./www，由宿主机 nginx 直接提供；这里只做存在性提示。
if [ ! -d www ]; then
  mkdir -p www
  echo "提示：./www 还不存在，已创建。" >&2
  echo "      先在 GitHub Actions 跑一次 publish-content，宿主机 nginx 才有内容可提供。" >&2
fi

# 命令行参数优先，其次 .env 里的 IMAGE_TAG，最后回退 latest
export IMAGE_TAG="${1:-${IMAGE_TAG:-latest}}"
echo "==> 部署 IMAGE_TAG=$IMAGE_TAG"

echo "==> 拉取镜像"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull

echo "==> 启动（等待健康检查）"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
  up -d --no-build --wait --wait-timeout 180

# 后端镜像没有 healthcheck，用 host 侧探测确认 Spring 真的起来了
echo "==> 检查后端"
backend_ok=0
for _ in $(seq 1 30); do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 \
    http://127.0.0.1:27000/ || true)"
  case "$code" in
    2??|3??|401|403) echo "    backend: HTTP $code"; backend_ok=1; break ;;
  esac
  sleep 2
done
if [ "$backend_ok" != "1" ]; then
  echo "    backend 未就绪（最后状态 $code），请查看：docker compose logs backend" >&2
  exit 1
fi

docker image prune -f >/dev/null 2>&1 || true

echo "==> 当前状态"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
echo
echo "完成。回滚：./deploy.sh <旧commit-sha>"
