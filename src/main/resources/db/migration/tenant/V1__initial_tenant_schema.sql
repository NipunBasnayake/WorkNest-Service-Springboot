CREATE TABLE IF NOT EXISTS employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_code VARCHAR(30) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    platform_user_id BIGINT,
    role VARCHAR(30) NOT NULL,
    designation VARCHAR(120),
    department VARCHAR(120),
    phone VARCHAR(30),
    salary DECIMAL(12,2),
    joined_date DATE,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_employees_code (employee_code),
    UNIQUE KEY uk_employees_email (email),
    INDEX idx_employees_role_status (role, status)
);

CREATE TABLE IF NOT EXISTS employee_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    skill_name VARCHAR(120) NOT NULL,
    skill_level VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_employee_skills_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    UNIQUE KEY uk_employee_skills_employee_skill (employee_id, skill_name),
    INDEX idx_employee_skills_employee (employee_id)
);

CREATE TABLE IF NOT EXISTS teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    manager_id BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_teams_manager FOREIGN KEY (manager_id) REFERENCES employees(id),
    UNIQUE KEY uk_teams_name (name)
);

CREATE TABLE IF NOT EXISTS team_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    functional_role VARCHAR(40) NOT NULL DEFAULT 'MEMBER',
    joined_at DATETIME NOT NULL,
    left_at DATETIME,
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_team_members_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    UNIQUE KEY uk_team_members_join (team_id, employee_id, joined_at),
    INDEX idx_team_members_employee (employee_id),
    INDEX idx_team_members_left_at (left_at)
);

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    start_date DATE,
    end_date DATE,
    status VARCHAR(30) NOT NULL,
    created_by_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_projects_created_by FOREIGN KEY (created_by_id) REFERENCES employees(id),
    INDEX idx_projects_status_date (status, start_date)
);

CREATE TABLE IF NOT EXISTS project_teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    CONSTRAINT fk_project_teams_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_project_teams_team FOREIGN KEY (team_id) REFERENCES teams(id),
    UNIQUE KEY uk_project_teams (project_id, team_id)
);

CREATE TABLE IF NOT EXISTS tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    assignee_id BIGINT,
    created_by_id BIGINT NOT NULL,
    due_date DATE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES employees(id),
    CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by_id) REFERENCES employees(id),
    INDEX idx_tasks_project_status (project_id, status),
    INDEX idx_tasks_assignee (assignee_id)
);

CREATE TABLE IF NOT EXISTS task_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    commented_by_id BIGINT NOT NULL,
    comment TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_task_comments_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_task_comments_commented_by FOREIGN KEY (commented_by_id) REFERENCES employees(id),
    INDEX idx_task_comments_task (task_id)
);

CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    check_in DATETIME,
    check_out DATETIME,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    UNIQUE KEY uk_attendance_employee_date (employee_id, work_date),
    INDEX idx_attendance_work_date (work_date)
);

CREATE TABLE IF NOT EXISTS leave_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    leave_type VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    approver_id BIGINT,
    reason TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_leave_requests_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_leave_requests_approver FOREIGN KEY (approver_id) REFERENCES employees(id),
    INDEX idx_leave_requests_status (status),
    INDEX idx_leave_requests_dates (start_date, end_date)
);

CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    created_by_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_announcements_created_by FOREIGN KEY (created_by_id) REFERENCES employees(id),
    INDEX idx_announcements_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    message VARCHAR(500) NOT NULL,
    reference_type VARCHAR(80),
    reference_id BIGINT,
    is_read BIT(1) NOT NULL,
    created_at DATETIME NOT NULL,
    read_at DATETIME,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES employees(id),
    INDEX idx_notifications_recipient_read (recipient_id, is_read),
    INDEX idx_notifications_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    uploaded_by_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_attachments_uploaded_by FOREIGN KEY (uploaded_by_id) REFERENCES employees(id),
    INDEX idx_attachments_entity (entity_type, entity_id),
    INDEX idx_attachments_uploaded_by (uploaded_by_id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT,
    actor_email VARCHAR(255),
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id BIGINT,
    metadata_json TEXT,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES employees(id),
    INDEX idx_audit_logs_action_entity (action, entity_type),
    INDEX idx_audit_logs_actor (actor_id),
    INDEX idx_audit_logs_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS hr_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    hr_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_hr_conversations_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_hr_conversations_hr FOREIGN KEY (hr_id) REFERENCES employees(id),
    UNIQUE KEY uk_hr_conversations_employee_hr (employee_id, hr_id)
);

CREATE TABLE IF NOT EXISTS hr_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    is_read BIT(1) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_hr_messages_conversation FOREIGN KEY (conversation_id) REFERENCES hr_conversations(id),
    CONSTRAINT fk_hr_messages_sender FOREIGN KEY (sender_id) REFERENCES employees(id),
    INDEX idx_hr_messages_conversation (conversation_id),
    INDEX idx_hr_messages_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS team_chats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_team_chats_team FOREIGN KEY (team_id) REFERENCES teams(id),
    UNIQUE KEY uk_team_chats_team (team_id)
);

CREATE TABLE IF NOT EXISTS team_chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_chat_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_team_chat_messages_chat FOREIGN KEY (team_chat_id) REFERENCES team_chats(id),
    CONSTRAINT fk_team_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES employees(id),
    INDEX idx_team_chat_messages_chat (team_chat_id),
    INDEX idx_team_chat_messages_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS chat_read_receipts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_type VARCHAR(20) NOT NULL,
    message_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    read_at DATETIME NOT NULL,
    CONSTRAINT fk_chat_read_receipts_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    UNIQUE KEY uk_chat_read_receipts_unique (chat_type, message_id, employee_id),
    INDEX idx_chat_read_receipts_message (chat_type, message_id)
);
