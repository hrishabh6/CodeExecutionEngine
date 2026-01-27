-- V3__add_updated_at_to_execution_metrics.sql
-- Adds the missing updated_at column to execution_metrics table

ALTER TABLE execution_metrics 
ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
