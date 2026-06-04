-- V4__Add_ModifiedBy_To_Revinfo.sql
ALTER TABLE `revinfo` ADD COLUMN `modified_by` VARCHAR(100) NULL;
