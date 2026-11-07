#!/usr/bin/env bash
# Creates or updates the Metech Lunch pilot on Azure Container Apps (API + dashboard in one app),
# inside the virtual network of the existing PostgreSQL server so the API can reach it privately.
#
#   deploy/azure.sh              deploy ghcr.io/<owner>/metech-lunch:latest
#   deploy/azure.sh <commit-sha> deploy a specific build
#
# Needs: Azure CLI signed in (az login), and backend/.env with DATABASE_USER, DATABASE_PASSWORD,
# JWT_SECRET and DEV_FIXED_OTP. Safe to re-run: existing resources are reused, the app is updated.
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1   # Git Bash on Windows would otherwise mangle Azure resource IDs ("/subscriptions/...")

# --- Settings ---
LOCATION=centralindia
APP_RG=rg-metech-lunch
ENV_NAME=cae-metech-lunch
APP_NAME=metech-lunch
IMAGE="ghcr.io/muralibedetanapalli-lab/metech-lunch:${1:-latest}"

DB_RG=rg-synchealth-centralindia
DB_SERVER=synchealth-pg
DB_NAME=lunch
VNET=synchealth-vnet
SUBNET=snet-metech-lunch          # new, used only by this app's Container Apps environment
SUBNET_PREFIX=10.20.8.0/27        # free range in synchealth-vnet (10.20.0.0/16)

# Small and scale-to-zero, to stay inside the Container Apps monthly free allowance.
CPU=0.25
MEMORY=0.5Gi

# --- Secrets from backend/.env (never printed) ---
env_value() {
  [ -f backend/.env ] || { echo "backend/.env is missing; copy backend/.env.example" >&2; exit 1; }
  grep -E "^$1=" backend/.env | tail -1 | cut -d= -f2- | sed -E 's/^"(.*)"$/\1/; s/^'"'"'(.*)'"'"'$/\1/'
}
DB_USER=$(env_value DATABASE_USER)
DB_PASSWORD=$(env_value DATABASE_PASSWORD)
JWT_SECRET=$(env_value JWT_SECRET)
LOGIN_CODE=$(env_value DEV_FIXED_OTP)
SHOW_CODES=$(env_value SHOW_OTP_ON_SCREEN)
REQUIRED=("DATABASE_USER:$DB_USER" "DATABASE_PASSWORD:$DB_PASSWORD" "JWT_SECRET:$JWT_SECRET")
# With codes shown on screen the server makes a random code per request, so no fixed code is needed.
[ "$SHOW_CODES" = "true" ] || REQUIRED+=("DEV_FIXED_OTP:$LOGIN_CODE")
for pair in "${REQUIRED[@]}"; do
  name=${pair%%:*}; value=${pair#*:}
  if [ -z "$value" ] || [[ "$value" == YOUR-* ]]; then
    echo "Set $name in backend/.env first." >&2; exit 1
  fi
done
if [ "$SHOW_CODES" != "true" ] && [ "$LOGIN_CODE" = "123456" ]; then
  echo "DEV_FIXED_OTP must not be 123456 on a public address; pick a private 6-digit code." >&2; exit 1
fi

step() { echo; echo "==> $*"; }

step "Resource group $APP_RG"
az group create -n "$APP_RG" -l "$LOCATION" -o none

step "Subnet $SUBNET in $VNET"
if ! az network vnet subnet show -g "$DB_RG" --vnet-name "$VNET" -n "$SUBNET" -o none 2>/dev/null; then
  az network vnet subnet create -g "$DB_RG" --vnet-name "$VNET" -n "$SUBNET" \
    --address-prefixes "$SUBNET_PREFIX" --delegations Microsoft.App/environments -o none
fi
SUBNET_ID=$(az network vnet subnet show -g "$DB_RG" --vnet-name "$VNET" -n "$SUBNET" --query id -o tsv)

step "Container Apps environment $ENV_NAME (first time takes several minutes)"
if ! az containerapp env show -g "$APP_RG" -n "$ENV_NAME" -o none 2>/dev/null; then
  az containerapp env create -g "$APP_RG" -n "$ENV_NAME" -l "$LOCATION" \
    --infrastructure-subnet-resource-id "$SUBNET_ID" --enable-workload-profiles \
    --logs-destination none -o none
fi

step "Database address inside the network"
# The server only has a private address. Its record in the private DNS zone resolves inside synchealth-vnet.
DNS_ZONE_ID=$(az postgres flexible-server show -g "$DB_RG" -n "$DB_SERVER" --query network.privateDnsZoneArmResourceId -o tsv)
DNS_ZONE=${DNS_ZONE_ID##*/}
DNS_ZONE_RG=$(echo "$DNS_ZONE_ID" | cut -d/ -f5)
if [ -z "${DB_HOST:-}" ]; then
  RECORDS=$(az network private-dns record-set a list -g "$DNS_ZONE_RG" -z "$DNS_ZONE" --query "[].name" -o tsv | tr -d '\r')
  if [ "$(echo "$RECORDS" | grep -c .)" != "1" ]; then
    echo "Expected one database record in $DNS_ZONE, found: $RECORDS. Re-run with DB_HOST=<record>.$DNS_ZONE" >&2; exit 1
  fi
  DB_HOST="$RECORDS.$DNS_ZONE"
fi
echo "Using $DB_HOST / database $DB_NAME as $DB_USER"

SECRETS=(db-password="$DB_PASSWORD" jwt-secret="$JWT_SECRET" login-code="${LOGIN_CODE:-unused}")
ENV_VARS=(
  DATABASE_URL="jdbc:postgresql://$DB_HOST:5432/$DB_NAME"
  DATABASE_USER="$DB_USER"
  DATABASE_PASSWORD=secretref:db-password
  JWT_SECRET=secretref:jwt-secret
  DEV_FIXED_OTP=secretref:login-code
  SEED_DEMO_DATA=true
  SHOW_OTP_ON_SCREEN="${SHOW_CODES:-false}"
)

step "Container app $APP_NAME ($IMAGE)"
if ! az containerapp show -g "$APP_RG" -n "$APP_NAME" -o none 2>/dev/null; then
  az containerapp create -g "$APP_RG" -n "$APP_NAME" --environment "$ENV_NAME" --image "$IMAGE" \
    --ingress external --target-port 8080 --cpu "$CPU" --memory "$MEMORY" \
    --min-replicas 0 --max-replicas 1 \
    --secrets "${SECRETS[@]}" --env-vars "${ENV_VARS[@]}" -o none
else
  az containerapp secret set -g "$APP_RG" -n "$APP_NAME" --secrets "${SECRETS[@]}" -o none
  BEFORE=$(az containerapp show -g "$APP_RG" -n "$APP_NAME" --query properties.latestRevisionName -o tsv)
  az containerapp update -g "$APP_RG" -n "$APP_NAME" --image "$IMAGE" --set-env-vars "${ENV_VARS[@]}" -o none
  AFTER=$(az containerapp show -g "$APP_RG" -n "$APP_NAME" --query properties.latestRevisionName -o tsv)
  if [ "$BEFORE" = "$AFTER" ]; then
    # Nothing in the template changed, so no new revision: restart it so it reads the updated secrets.
    az containerapp revision restart -g "$APP_RG" -n "$APP_NAME" --revision "$AFTER" -o none
  fi
fi

FQDN=$(az containerapp show -g "$APP_RG" -n "$APP_NAME" --query properties.configuration.ingress.fqdn -o tsv)
CURRENT_URL=$(az containerapp show -g "$APP_RG" -n "$APP_NAME" \
  --query "properties.template.containers[0].env[?name=='PUBLIC_BASE_URL'].value | [0]" -o tsv)
if [ "$CURRENT_URL" != "https://$FQDN" ]; then
  # Payment checkout links point back at this address.
  az containerapp update -g "$APP_RG" -n "$APP_NAME" --set-env-vars PUBLIC_BASE_URL="https://$FQDN" -o none
fi

step "Waiting for https://$FQDN/api/health (a cold start takes up to a minute)"
for _ in $(seq 1 30); do
  if curl -fsS --max-time 20 "https://$FQDN/api/health" >/dev/null 2>&1; then
    echo "Up: https://$FQDN"
    exit 0
  fi
  sleep 10
done
echo "Not answering yet. Logs: az containerapp logs show -g $APP_RG -n $APP_NAME --type console --tail 100" >&2
exit 1
