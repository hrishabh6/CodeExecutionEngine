-- V2__create_execution_metrics_table.sql
-- Creates the execution_metrics table for analytics

CREATE TABLE IF NOT EXISTS execution_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- Reference to submission
    submission_id VARCHAR(36) NOT NULL,
    
    -- Timing breakdown (all in milliseconds)
    queue_wait_ms INT,
    compilation_ms INT,
    execution_ms INT,
    total_ms INT,
    
    -- Resource usage
    peak_memory_kb INT,
    cpu_time_ms INT,
    
    -- System info
    worker_id VARCHAR(50),
    execution_node VARCHAR(100),
    
    -- Cache info
    used_cache BOOLEAN DEFAULT FALSE,
    
    -- Docker container ID
    container_id VARCHAR(64),
    
    -- Test case timing breakdown (JSON array)
    test_case_timings JSON,
    
    -- Audit timestamp
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for submission lookup
CREATE INDEX idx_metrics_submission_id ON execution_metrics(submission_id);
CREATE INDEX idx_metrics_worker ON execution_metrics(worker_id);
