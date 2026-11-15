# 本镜像只提供 nginx 与站点配置，**不包含任何文章内容**。
#
# 内容通过 /usr/share/nginx/html 的 volume 挂载提供：
#   ./www:/usr/share/nginx/html:ro
# GitHub Actions 的 publish-content job 用 rsync 把 dist 写进该目录。
# 因此公开镜像被任何人拉走也不会带上你的文章正文。
FROM nginxinc/nginx-unprivileged:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

# 基础镜像以非 root 用户（uid 101）运行，RUN 无法写 /usr/share/nginx/html，
# 因此用 COPY 放入占位页——COPY 由构建器以 root 执行，不受镜像 USER 限制。
COPY docker/placeholder.html /usr/share/nginx/html/index.html

EXPOSE 8080
