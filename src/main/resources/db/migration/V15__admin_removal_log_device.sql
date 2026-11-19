-- The removal log keeps the names as they were when the event happened, and the
-- device the actor used. Names are copied, not looked up when the log is read,
-- because Administrator.reactive() can change full_name and a log must not
-- change afterwards.
--
-- Rows written before this migration get today's names, which is the best that
-- is known, and `unknown` as device, which was never recorded.
-- The columns are added empty, filled, and only the made NOT NULL, because
--PostgreSQL refuses NOT NULL on a column that exiting rows leave empty.

ALTER TABLE administrator_removal_log
ADD COLUMN administrator_name VARCHAR(100),
ADD COLUMN actor_name VARCHAR(100),
ADD COLUMN device VARCHAR(100) NOT NULL DEFAULT 'unknown';

UPDATE administrator_removal_log 
SET administrator_name = a.full_name
FROM administrator a
WHERE a.id = administrator_id;

UPDATE administrator_removal_log
SET actor_name = a.full_name
FROM administrator a
WHERE a.id = actor_id;

ALTER TABLE administrator_removal_log
Alter COLUMN administrator_name SET NOT NULL,
Alter COLUMN actor_name SET NOT NULL;
