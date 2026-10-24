SHELL := /bin/bash
.DEFAULT_GOAL := help

COMPOSE  ?= docker compose
DEV      := $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml
PROD     := $(COMPOSE) -f docker-compose.yml -f docker-compose.prod.yml --profile prod
ENV_FILE ?= .env

# Maven s'exécute dans un conteneur JDK 21 : seul Docker est requis sur le poste (pas de JDK local).
# Avec un JDK 21 local : make test-unit MVN=mvn
MVN_IMAGE ?= maven:3.9-eclipse-temurin-21
MVN_CORE ?= MSYS_NO_PATHCONV=1 docker run --rm -v "$(CURDIR)/beep/core:/src" -v beep-m2:/root/.m2 -w /src $(MVN_IMAGE) mvn -B -ntp
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

seed: ## Données de démo : utilisateur demo@beep.localhost + staff admin@beep.localhost (le TOTP s'affiche dans les logs)
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
.PHONY: fmt lint test test-unit test-int test-contract test-e2e test-web
fmt: ## Formate le code front (Prettier)
	npm run format -w beep-web -w backoffice-web

lint: ## Typage TypeScript strict + contrastes des tokens + règles d'architecture
	npm run lint
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

test: test-unit test-int test-contract ## Tous les tests rapides

# ---------- Contrats ----------
.PHONY: openapi-lint
openapi-lint: ## Valide contracts/*.yaml
	./scripts/openapi-lint.sh

# ---------- Production ----------
.PHONY: build prod-up prod-down release
build: ## Construit les images de production
	$(PROD) build

prod-up: ## Démarre la production (avec archivage WAL-G)
	$(PROD) up -d

prod-down: ## Arrête la production
	$(PROD) down

release: lint test build ## Pipeline de release locale
	@echo "Images prêtes."
