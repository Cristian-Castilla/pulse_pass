-- =============================================================================
-- V3__add_streaming_url_to_event.sql
-- Agrega streaming_url VARCHAR(500) nullable a events sin modificar V1
-- =============================================================================

ALTER TABLE events
    ADD COLUMN streaming_url VARCHAR(500);
