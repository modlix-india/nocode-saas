ALTER TABLE `entity_processor`.`entity_processor_products`
    ADD COLUMN `LOGO_FILE_DETAIL` JSON NULL COMMENT 'File Details if product has a logo file' AFTER `PRODUCT_TEMPLATE_ID`,
    ADD COLUMN `BANNER_FILE_DETAIL` JSON NULL COMMENT 'File Details if product has a banner file' AFTER `LOGO_FILE_DETAIL`;
