-- Runs only on an empty Postgres volume. One database + owner per service.
-- Local development values only.
CREATE USER keycloak WITH PASSWORD 'keycloak';
CREATE DATABASE keycloak_db OWNER keycloak;

CREATE USER identity WITH PASSWORD 'identity';
CREATE DATABASE identity_db OWNER identity;

CREATE USER pet WITH PASSWORD 'pet';
CREATE DATABASE pet_db OWNER pet;

CREATE USER health WITH PASSWORD 'health';
CREATE DATABASE health_db OWNER health;

CREATE USER notification WITH PASSWORD 'notification';
CREATE DATABASE notification_db OWNER notification;
