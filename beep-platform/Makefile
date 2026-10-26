SHELL := /bin/bash
.DEFAULT_GOAL := help

COMPOSE  ?= docker compose
ENV_FILE ?= .env
PROD_PROJECT ?= beep-prod
DEPLOY_TIMEOUT ?= 180
DEV      := $(COMPOSE) --env-file "$(ENV_FILE)" -f docker-compose.yml -f docker-compose.dev.yml
PROD     := $(COMPOSE) --project-name "$(PROD_PROJECT)" --env-file "$(ENV_FILE)" -f docker-compose.yml -f docker-compose.prod.yml --profile prod

# Maven s'exécute dans un conteneur JDK 21 : seul Docker est requis sur le poste (pas de JDK local).
# Avec un JDK 21 local : make test-unit MVN=mvn
MVN_IMAGE ?= maven:3.9-eclipse-temurin-21
MVN_CORE ?= MSYS_NO_PATHCONV=1 docker run --rm -v "$(CURDIR)/beep/core:/src" -v "$(CURDIR)/contracts:/contracts:ro" -v beep-m2:/root/.m2 -w /src $(MVN_IMAGE) mvn -B -ntp
MVN_BO   ?= MSYS_NO_PATHCONV=1 docker run --rm -v "$(CURDIR)/backoffice/api:/src" -v beep-m2:/root/.m2 -w /src $(MVN_IMAGE) mvn -B -ntp
ifdef MVN
MVN_CORE := cd beep/core && $(MVN) -B -ntp
MVN_BO   := cd backoffice/api && $(MVN) -B -ntp
endif

.PHONY: help
help: ## Affiche cette aide
	@grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}'

# ---------- Environnement ----------
.PHONY: init up down restart logs ps clean
init: ## Crée .env à partir de .env.example et génère les secrets manquants
	@./scripts/init-env.sh $(ENV_FILE)

up: init tokens ## Démarre la stack de développement (Postgres, migrations, services)
	$(DEV) up -d --build
	@echo ""
	@echo "  Site         http://beep.localhost"
	@echo "  Back office  http://dashboard.beep.localhost"
	@echo "  E-mails      http://mail.beep.localhost   (Mailpit)"

down: ## Arrête la stack
	$(DEV) down

restart: down up ## Redémarre la stack

logs: ## Suit les logs (make logs s=core)
	$(DEV) logs -f $(s)

ps: ## État des services
	$(DEV) ps

clean: ## Supprime conteneurs ET volumes (données perdues)
	$(DEV) down -v --remove-orphans

# ---------- Charte graphique ----------
.PHONY: tokens contrast
tokens: ## Régénère dist/tokens.css et TOKENS.md depuis tokens.json
	@node packages/design-tokens/build.mjs

contrast: ## Vérifie les contrastes WCAG des paires de couleurs (clair + sombre)
	@node packages/design-tokens/contrast.mjs

# ---------- Base de données (PostgreSQL + Flyway) ----------
.PHONY: migrate migrate-validate seed db-shell
migrate: ## Applique les migrations Flyway (core + backoffice)
	$(DEV) run --rm core-migrate
	$(DEV) run --rm backoffice-migrate

migrate-validate: ## Rejoue TOUTES les migrations sur une base vierge et vérifie leur intégrité (PostgreSQL réel)
	./scripts/migrate-validate.sh

seed: ## Démo : demo@beep.localhost (lien /demo, QR, page /atelier-demo) + staff admin@beep.localhost (le TOTP s'affiche dans les logs)
	$(DEV) run --rm -e SPRING_PROFILES_ACTIVE=dev,seed core
	$(DEV) run --rm -e SPRING_PROFILES_ACTIVE=dev,seed backoffice-api

db-shell: ## Shell psql (make db-shell db=beep|backoffice)
	$(DEV) exec postgres sh -c 'psql -U "$$POSTGRES_USER" -d $(or $(db),beep)'

# ---------- Sauvegarde / restauration ----------
.PHONY: backup restore
backup: ## Dump logique des deux bases (dev) -> ./backups
	./scripts/backup.sh

restore: ## Restauration (dev : dump ; prod : PITR) make restore from=<fichier|horodatage>
	./scripts/restore.sh $(from)

# ---------- Qualité ----------
.PHONY: fmt lint test test-unit test-int test-contract test-e2e test-web test-load reserved-slugs
fmt: ## Formate le code front (Prettier)
	npm run format -w beep-web -w backoffice-web

lint: ## Typage TypeScript strict + contrastes des tokens + slugs réservés = routes du site
	npm run lint
	@node scripts/reserved-slugs-check.mjs > /dev/null && echo "slugs réservés OK"
	@node packages/design-tokens/contrast.mjs > /dev/null && echo "contrastes OK"

test-unit: ## Unitaires : domaine, cas d'usage (doublures) + règles ArchUnit
	$(MVN_CORE) test
	$(MVN_BO) test

test-int: ## Intégration : adapters sur un PostgreSQL réel (migrations rejouées de zéro)
	./scripts/test-int.sh

test-contract: ## Contrat core <-> backoffice : spec valide + core conforme + client conforme
	./scripts/contract-test.sh

test-web: ## Build des deux fronts (typage + compilation Next.js)
	npm run build:web
	npm run build:bo

test-e2e: ## Bout en bout sur la stack Compose : inscription -> e-mail -> connexion -> vue staff
	./scripts/e2e.sh

test-load: ## Charge k6 sur la redirection (stack démarrée + make seed) : make test-load RATE=1000 DURATION=60s
	./scripts/load-test.sh

test-load-phase3: ## Ingestion et redirection simultanées (COLLECT_KEYS requis ; comptes de recette dédiés)
	bash ./scripts/load-phase3.sh

reserved-slugs: ## Vérifie que chaque route du site (Caddy) est un slug réservé du core
	@node scripts/reserved-slugs-check.mjs

test: test-unit test-int test-contract ## Tous les tests rapides

# ---------- Contrats ----------
.PHONY: openapi-lint
openapi-lint: ## Valide contracts/*.yaml
	./scripts/openapi-lint.sh

# ---------- Production ----------
.PHONY: build prod-check deploy-prod prod-up prod-down prod-logs prod-ps release
prod-check: ## Vérifie la configuration de production sans afficher les secrets ni démarrer de service
	@set -o pipefail; $(PROD) config --format json | node scripts/prod-check.mjs

build: ## Construit les images de production
	$(PROD) build

deploy-prod: ## Vérifie, construit, migre puis déploie la production et attend les services sains
	@$(MAKE) --no-print-directory prod-check
	@$(MAKE) --no-print-directory build
	$(PROD) up -d --wait --wait-timeout $(DEPLOY_TIMEOUT) postgres
	$(PROD) run --rm --no-deps core-migrate
	$(PROD) run --rm --no-deps backoffice-migrate
	$(PROD) up -d --wait --wait-timeout $(DEPLOY_TIMEOUT)
	$(PROD) exec -T proxy caddy reload --config /etc/caddy/Caddyfile
	$(PROD) ps

prod-up: prod-check ## Démarre les images de production et attend les services sains
	$(PROD) up -d --wait --wait-timeout $(DEPLOY_TIMEOUT)

prod-down: ## Arrête la production
	$(PROD) down

prod-logs: ## Suit les logs de production (make prod-logs s=core)
	$(PROD) logs -f $(s)

prod-ps: ## État des services de production
	$(PROD) ps

release: lint test build ## Pipeline de release locale
	@echo "Images prêtes."
