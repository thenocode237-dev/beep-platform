# Contexte de build : la racine du monorepo (le site dépend de packages/ui et packages/design-tokens).
FROM node:22-alpine AS build
WORKDIR /repo
COPY package.json package-lock.json ./
COPY packages ./packages
COPY beep/web/package.json beep/web/
COPY backoffice/web/package.json backoffice/web/
RUN npm ci --no-audit --no-fund --workspace beep-web --include-workspace-root
COPY beep/web beep/web
ARG BEEP_PUBLIC_URL=https://beepclick.app
ENV BEEP_PUBLIC_URL=$BEEP_PUBLIC_URL
RUN npm run tokens && npm run build -w beep-web

FROM node:22-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /repo
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0 NEXT_TELEMETRY_DISABLED=1
COPY --from=build --chown=app:app /repo/beep/web/.next/standalone ./
COPY --from=build --chown=app:app /repo/beep/web/.next/static ./beep/web/.next/static
COPY --from=build --chown=app:app /repo/beep/web/public ./beep/web/public
USER app
EXPOSE 3000
CMD ["node", "beep/web/server.js"]
