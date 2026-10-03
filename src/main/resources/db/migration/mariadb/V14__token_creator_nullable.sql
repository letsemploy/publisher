-- A service token outlives the person who created it (spec 2.8, 2.13). Tokens
-- belong to the employer, so deleting one's own account must not take a working
-- integration with it - nor be refused because of one. The creator becomes
-- optional and is forgotten when that account is deleted; the register then says
-- "a deleted account", and the audit log still names who created it (spec 3.12).
--
-- Three statements: InnoDB will not change a column a foreign key still points
-- at, so the key goes first and comes back with the new rule (see V4).
ALTER TABLE service_tokens DROP FOREIGN KEY fk_service_tokens_created_by;

ALTER TABLE service_tokens MODIFY created_by_id UUID NULL;

ALTER TABLE service_tokens
    ADD CONSTRAINT fk_service_tokens_created_by FOREIGN KEY (created_by_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE SET NULL;
