-- V1__init_auth_schema.sql
-- Initial schema for auth-service: users, roles, and projects tables.

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(50)  NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE IF NOT EXISTS projects (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    owner_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    api_key_hash   VARCHAR(64)  UNIQUE,
    api_key_active BOOLEAN      NOT NULL DEFAULT TRUE
);
