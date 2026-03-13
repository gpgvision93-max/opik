--liquibase formatted sql
--changeset thiagohora:000056_add_project_id_to_entity_tables
--comment: Add nullable project_id columns to datasets, prompts, and dashboards tables for project-scoped operations

ALTER TABLE datasets
    ADD COLUMN project_id CHAR(36) NULL DEFAULT NULL,
    ADD CONSTRAINT fk_datasets_project_id FOREIGN KEY (project_id) REFERENCES projects(id);

ALTER TABLE prompts
    ADD COLUMN project_id CHAR(36) NULL DEFAULT NULL,
    ADD CONSTRAINT fk_prompts_project_id FOREIGN KEY (project_id) REFERENCES projects(id);

ALTER TABLE dashboards
    ADD COLUMN project_id CHAR(36) NULL DEFAULT NULL,
    ADD CONSTRAINT fk_dashboards_project_id FOREIGN KEY (project_id) REFERENCES projects(id);

--rollback ALTER TABLE datasets DROP FOREIGN KEY fk_datasets_project_id, DROP COLUMN project_id;
--rollback ALTER TABLE prompts DROP FOREIGN KEY fk_prompts_project_id, DROP COLUMN project_id;
--rollback ALTER TABLE dashboards DROP FOREIGN KEY fk_dashboards_project_id, DROP COLUMN project_id;
