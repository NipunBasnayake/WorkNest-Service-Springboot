-- Manual master-schema migration script for platform_announcements.
-- Master DB currently uses spring.jpa.hibernate.ddl-auto=update in this project,
-- so this script is provided for controlled/manual migration environments.

CREATE TABLE IF NOT EXISTS platform_announcements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    created_by_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_platform_announcements_created_by FOREIGN KEY (created_by_id) REFERENCES platform_users(id)
);
