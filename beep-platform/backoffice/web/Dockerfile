# Contexte de build : la racine du monorepo (dépend de packages/ui et packages/design-tokens).
FROM node:22-alpine AS build
WORKDIR /repo
COPY package.json package-lock.json ./
COPY packages ./packages
COPY beep/web/package.json beep/web/
COPY backoffice/web/package.json backoffice/web/
RUN npm ci --no-audit --no-fund --workspace backoffice-web --include-workspace-root
COPY backoffice/web backoffice/web
RUN npm run tokens && npm run build -w backoffice-web

FROM node:22-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /repo
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0 NEXT_TELEMETRY_DISABLED=1
COPY --from=build --chown=app:app /repo/backoffice/web/.next/standalone ./
COPY --from=build --chown=app:app /repo/backoffice/web/.next/static ./backoffice/web/.next/static
USER app
EXPOSE 3000
CMD ["node", "backoffice/web/server.js"]
