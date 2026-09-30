FROM node:24.21.0-alpine

WORKDIR /studio
RUN chown node:node /studio
USER node

COPY --chown=node:node package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY --chown=node:node . .

EXPOSE 3333
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s \
    CMD wget -q -O /dev/null "http://127.0.0.1:3333${SANITY_STUDIO_BASEPATH:-}/" || exit 1
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "3333"]
