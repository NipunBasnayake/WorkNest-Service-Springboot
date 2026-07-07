CREATE TABLE job_positions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(180) NOT NULL,
    department VARCHAR(120),
    description TEXT,
    employment_type VARCHAR(30) NOT NULL,
    location VARCHAR(160),
    openings INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    published BIT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_job_positions_status (status),
    INDEX idx_job_positions_department (department)
);

CREATE TABLE candidates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(180) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(30),
    current_title VARCHAR(160),
    years_of_experience INT,
    source VARCHAR(120),
    summary TEXT,
    resume_file_name VARCHAR(255),
    resume_file_url VARCHAR(1000),
    resume_mime_type VARCHAR(120),
    resume_file_size_bytes BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uq_candidates_email (email),
    INDEX idx_candidates_email (email),
    INDEX idx_candidates_full_name (full_name)
);

CREATE TABLE candidate_applications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    candidate_id BIGINT NOT NULL,
    job_position_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    cover_letter TEXT,
    expected_salary DECIMAL(19,2),
    recruiter_notes TEXT,
    rejected_reason TEXT,
    created_by_employee_id BIGINT,
    applied_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    offered_at DATETIME,
    hired_at DATETIME,
    INDEX idx_candidate_applications_job_status (job_position_id, status),
    INDEX idx_candidate_applications_candidate (candidate_id)
);

CREATE TABLE candidate_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    candidate_id BIGINT NOT NULL,
    author_employee_id BIGINT,
    message TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_candidate_comments_candidate (candidate_id)
);

CREATE TABLE interviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    interviewer_employee_id BIGINT NOT NULL,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    scheduled_at DATETIME NOT NULL,
    location VARCHAR(255),
    meeting_link VARCHAR(1000),
    notes TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_interviews_application (application_id),
    INDEX idx_interviews_scheduled_at (scheduled_at)
);

CREATE TABLE interview_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    interview_id BIGINT NOT NULL UNIQUE,
    reviewer_employee_id BIGINT,
    rating INT,
    recommendation VARCHAR(20),
    strengths TEXT,
    concerns TEXT,
    notes TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_interview_feedback_interview (interview_id)
);