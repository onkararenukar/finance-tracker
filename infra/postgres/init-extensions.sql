-- init-extensions.sql
--
-- Runs automatically ONCE, the first time the postgres container starts
-- with an empty data volume (this is Postgres's official docker-entrypoint
-- convention: anything in /docker-entrypoint-initdb.d/ gets executed).
--
-- We enable pgvector here, at the superuser/bootstrap level, so that the
-- Flyway migrations owned by individual microservices can rely on the
-- `vector` type already existing (Flyway's own DB user may not have
-- CREATE EXTENSION rights in a locked-down environment).

CREATE EXTENSION IF NOT EXISTS vector;
