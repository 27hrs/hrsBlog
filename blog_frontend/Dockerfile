# 本镜像只提供 nginx 与站点配置，**不包含任何文章内容**。
#
# 内容通过 /usr/share/nginx/html 的 volume 挂载提供：
#   ./www:/usr/share/nginx/html:ro
# GitHub Actions 的 publish-content job 用 rsync 把 dist 直接写进 ./www，
# 因此公开镜像被任何人拉走也不会带上你的文章正文。
FROM nginxinc/nginx-unprivileged:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

# 内容未挂载时给出可读的提示，而不是空白页或 403
RUN printf '%s\n' \
      '<!doctype html><meta charset="utf-8"><title>HrsBlog</title>' \
      '<p>内容未挂载：请检查 compose 中的 ./www:/usr/share/nginx/html 挂载。</p>' \
      > /usr/share/nginx/html/index.html

EXPOSE 8080
