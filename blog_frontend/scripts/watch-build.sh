#!/bin/sh
# blog-builder 容器的入口脚本：定时检查后端内容 → 变化则重新生成静态站点 → 发布到共享目录。
#
# 设计目标：
#   - 内容更新不产生镜像、不重启、不拉包，容器自己把 HTML 生成好
#   - 构建失败时保留线上旧版本，绝不把半成品或空目录暴露给访客
#   - 空闲时只做一次同步 + 指纹计算（几百毫秒），不重复构建
set -eu

SRC_DIR="${SRC_DIR:-/app}"
OUT_DIR="${OUT_DIR:-/www}"
INTERVAL="${INTERVAL:-300}"
PNPM_VERSION="${PNPM_VERSION:-10.34.1}"

log() { printf '[builder] %s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

if [ ! -f "$SRC_DIR/package.json" ]; then
  log "错误：$SRC_DIR 下没有 package.json"
  log "请把仓库的 blog_frontend 目录放到宿主机 ./src/blog_frontend（见 compose 的挂载）"
  exit 1
fi

cd "$SRC_DIR"
mkdir -p "$OUT_DIR"

corepack enable >/dev/null 2>&1 || true
corepack prepare "pnpm@${PNPM_VERSION}" --activate >/dev/null 2>&1 || true

if [ ! -d node_modules ]; then
  log "首次安装依赖（依赖落盘在挂载目录，重启后复用）"
  pnpm install --frozen-lockfile
fi

publish() {
  rm -rf .vitepress/dist
  if ! pnpm exec vitepress build; then
    log "构建失败，线上保持上一版"
    return 1
  fi
  if ! pnpm verify; then
    log "产物校验失败，线上保持上一版"
    return 1
  fi

  # 先复制、再删除多余文件：任何时刻 OUT_DIR 都不会变成空目录
  cp -a .vitepress/dist/. "$OUT_DIR/"
  ( cd .vitepress/dist && find . -type f | sort ) > /tmp/new.list
  ( cd "$OUT_DIR"       && find . -type f | sort ) > /tmp/old.list
  comm -13 /tmp/new.list /tmp/old.list > /tmp/del.list 2>/dev/null || true
  while IFS= read -r file; do
    [ -n "$file" ] && rm -f "$OUT_DIR/$file"
  done < /tmp/del.list

  log "已发布 $(find "$OUT_DIR" -type f | wc -l) 个文件"
  return 0
}

LAST=""

cycle() {
  if ! pnpm sync; then
    log "同步失败（后端不可用？），本轮跳过"
    return 0
  fi

  current="$(pnpm exec node scripts/fingerprint.mjs)" || {
    log "指纹计算失败，本轮跳过"
    return 0
  }

  if [ "$current" = "$LAST" ] && [ -f "$OUT_DIR/index.html" ]; then
    return 0
  fi

  log "内容有变化，开始构建"
  if publish; then
    LAST="$current"
  fi
}

# 启动即跑一轮：容器首次拉起时共享目录还是空的
cycle
while :; do
  sleep "$INTERVAL"
  cycle
done
