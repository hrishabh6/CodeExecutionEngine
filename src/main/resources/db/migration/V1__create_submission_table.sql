-- V1__create_submission_table.sql
-- Creates the submission table for tracking code submissions

CREATE TABLE IF NOT EXISTS submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- Unique identifier for external reference
    submission_id VARCHAR(36) NOT NULL UNIQUE,
    
    -- Foreign keys (references to other services)
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    
    -- Code details
    language VARCHAR(20) NOT NULL,
    code TEXT NOT NULL,
    
    -- Status tracking
    status VARCHAR(20) NOT NULL,
    verdict VARCHAR(30),
    
    -- Performance metrics
    runtime_ms INT,
    memory_kb INT,
    
    -- Test case results (JSON array)
    test_results JSON,
    
    -- Error information
    error_message TEXT,
    compilation_output TEXT,
    
    -- Timestamps
    queued_at DATETIME NOT NULL,
    started_at DATETIME,
    completed_at DATETIME,
    
    -- Client metadata
    ip_address VARCHAR(45),
    user_agent TEXT,
    
    -- Worker info
    worker_id VARCHAR(50),
    
    -- Audit timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_submission_id ON submission(submission_id);
CREATE INDEX idx_user_status ON submission(user_id, status);
CREATE INDEX idx_question_status ON submission(question_id, status);
CREATE INDEX idx_status_queued ON submission(status, queued_at);
CREATE INDEX idx_user_queued ON submission(user_id, queued_at DESC);
