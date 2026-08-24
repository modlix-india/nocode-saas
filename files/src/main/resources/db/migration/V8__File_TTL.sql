USE `files`;

-- Retention as a property of the file, decided by whoever created it.
--
-- The alternative was a sweep somewhere else that worked out which files were old enough to remove.
-- That is the same design, with the crucial difference that the deciding is done by a caller who has
-- to identify the files it owns, and getting that filter wrong deletes somebody else's. It nearly
-- did: two thirds of the WhatsApp media rows point at product brochures shared across many messages,
-- and an age-based sweep over them would have deleted a product's asset library because a lead was
-- sent a copy thirty-one days ago.
--
-- Expressed here, only a file explicitly created with a lifetime is ever eligible. A file uploaded
-- without one cannot be removed by the cleanup no matter how old it gets, which is the right default
-- for everything already in the bucket.
--
-- Minutes from the last write rather than an absolute timestamp, deliberately. Re-uploading the same
-- path extends its life, which is what "after last update/create" has to mean: an avatar refreshed
-- every week should never expire, and it would if the clock started at first creation.
ALTER TABLE `files_file_system`
    ADD COLUMN `EXPIRES_AFTER_MINUTES` INT UNSIGNED NULL
        COMMENT 'Minutes after the last create/update this file may be deleted. NULL means keep forever.'
        AFTER `SIZE`;

-- The cleanup asks one question: which files with a lifetime have outlived it. Without this it is a
-- full scan of every file in the system, on a schedule, forever.
CREATE INDEX `IDX_FILES_FS_EXPIRY`
    ON `files_file_system` (`EXPIRES_AFTER_MINUTES`, `UPDATED_AT`);
