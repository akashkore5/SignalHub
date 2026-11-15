#!/usr/bin/env bash
# ============================================================================
#  Deploy SignalHub on the Contabo box.
#
#  Config lives in two files OUTSIDE the repo, because application.properties
#  is gitignored (it holds real credentials) and therefore absent from a fresh
#  clone — a container built from the repo alone has no configuration at all:
#
#    /var/www/signalhub/config/application.properties   full config, mode 600
#    /var/www/signalhub/config/env                      on-box overrides
#
#  Spring Boot gives ./config/application.properties precedence over the
#  classpath, so the file is mounted at /event/config/ rather than baked in.
#
#  Usage:  deploy/deploy.sh [image-tag]
# ============================================================================
set -euo pipefail

APP_DIR=/var/www/signalhub/app
CONF_DIR=/var/www/signalhub/config
NAME=signalhub
PORT=8091          # host side; 8081 in the container (8090 is dyopact-core)
TAG="${1:-$(git -C "$APP_DIR" rev-parse --short HEAD)}"

say() { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; exit 1; }

[[ -f "$CONF_DIR/application.properties" ]] || die "missing $CONF_DIR/application.properties"
[[ -f "$CONF_DIR/env" ]]                    || die "missing $CONF_DIR/env"

say "Building $NAME:$TAG"
docker build -t "$NAME:$TAG" -t "$NAME:latest" "$APP_DIR"

# Keep the previous container until the new one is confirmed healthy, so a bad
# build does not leave the notification pipeline down.
OLD_ID="$(docker ps -aq -f name="^${NAME}$" || true)"
[[ -n "$OLD_ID" ]] && { say "Stopping previous container"; docker rm -f "$NAME" >/dev/null; }

say "Starting $NAME"
docker run -d --name "$NAME" --restart unless-stopped \
  --network redpanda-net \
  --env-file "$CONF_DIR/env" \
  -v "$CONF_DIR/application.properties:/event/config/application.properties:ro" \
  -p "127.0.0.1:${PORT}:8081" \
  --memory 1g --memory-swap 1g \
  "$NAME:$TAG" >/dev/null

# Redis and Prometheus live on other networks; one network per docker run.
docker network connect redis-net "$NAME" 2>/dev/null || true

say "Waiting for health"
for i in $(seq 1 60); do
  if curl -fsS --max-time 3 "http://127.0.0.1:${PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
    say "healthy after ${i}s"
    # Health says the process is up; it does NOT say the Kafka listeners
    # attached. A fatal consumer error (missing topic ACL) stops a listener
    # container while the app still reports UP — so check explicitly.
    if docker logs --since 2m "$NAME" 2>&1 | grep -q 'Fatal consumer exception'; then
      docker logs --since 2m "$NAME" 2>&1 | grep -B2 'Fatal consumer exception' | tail -5
      die "a Kafka listener died on startup (see above) — the service is up but partly deaf"
    fi
    docker logs --since 2m "$NAME" 2>&1 | grep 'partitions assigned' | sed 's/^/    /' | tail -8
    exit 0
  fi
  sleep 1
done

docker logs --tail 40 "$NAME" >&2
die "did not become healthy within 60s"
