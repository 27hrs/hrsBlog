FROM node:22-alpine AS build

WORKDIR /app

RUN corepack enable && corepack prepare pnpm@10.34.1 --activate

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .

ARG BLOG_API_BASE=""
ARG BLOG_SITE_URL=""
ARG BLOG_SYNC_STRICT="0"
ENV BLOG_API_BASE=$BLOG_API_BASE \
    BLOG_SITE_URL=$BLOG_SITE_URL \
    BLOG_SYNC_STRICT=$BLOG_SYNC_STRICT

RUN pnpm build

FROM nginxinc/nginx-unprivileged:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/.vitepress/dist /usr/share/nginx/html

EXPOSE 8080
