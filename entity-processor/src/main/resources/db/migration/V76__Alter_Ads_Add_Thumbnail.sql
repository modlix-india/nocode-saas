-- Ad creative thumbnail/image for the campaign report's ad-image column.
-- Populated during discovery from the platform (Meta: creative{thumbnail_url};
-- Google: best-effort image url). NULL until the next discovery sync.

ALTER TABLE `entity_processor`.`entity_processor_ads`
    ADD COLUMN `THUMBNAIL_URL` VARCHAR(1024) DEFAULT NULL
        COMMENT 'Ad creative thumbnail/image URL from the platform.' AFTER `AD_NAME`,
    ADD COLUMN `CREATIVE_TYPE` VARCHAR(64) DEFAULT NULL
        COMMENT 'Creative type: IMAGE / VIDEO / CAROUSEL / etc.' AFTER `THUMBNAIL_URL`;
