# Resume renderer (docs/phase2/03 §5.3). Build from the repository root:
#   docker build -f renderer/Dockerfile -t jobproof-renderer .
# Stage 1 builds the frontend, whose print.html and template assets the renderer serves to Chromium.
FROM node:24.19.0-bookworm-slim AS web
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm install --global npm@11.5.1 && npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# Stage 2: Playwright's image ships Chromium 141 (playwright-core 1.62.1) with its system libraries.
FROM mcr.microsoft.com/playwright:v1.62.1-noble
ENV NODE_ENV=production \
    RENDERER_PORT=3100 \
    RENDERER_PRINT_DIR=/app/print \
    RENDERER_POOL_SIZE=2
WORKDIR /app/renderer
COPY renderer/package.json renderer/package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY renderer/src ./src
COPY --from=web /src/frontend/dist /app/print
USER pwuser
EXPOSE 3100
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=5 \
  CMD node -e "fetch('http://127.0.0.1:3100/readyz').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"
CMD ["node", "src/server.ts"]
