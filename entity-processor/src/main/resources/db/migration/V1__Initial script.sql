/* Creating Database */

DROP DATABASE IF EXISTS `entity_collector`;

CREATE DATABASE IF NOT EXISTS `entity_collector` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `entity_collector`;

DROP TABLE IF EXISTS `entity_collector`.`entity_collector_model`;
