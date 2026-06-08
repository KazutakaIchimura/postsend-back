CREATE TABLE IF NOT EXISTS roles (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS staffs (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL,
    email                 VARCHAR(255) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    role_id               BIGINT       NOT NULL,
    is_active             BOOLEAN      DEFAULT TRUE,
    force_password_change BOOLEAN      DEFAULT TRUE,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE IF NOT EXISTS users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    name_kana  VARCHAR(100),
    birth_date DATE,
    notes      TEXT,
    is_active  BOOLEAN   DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS offices (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    postal_code VARCHAR(8),
    building    VARCHAR(200),
    address     VARCHAR(500),
    phone       VARCHAR(20),
    is_active   BOOLEAN   DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_offices (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    office_id  BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_office (user_id, office_id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (office_id) REFERENCES offices(id)
);

CREATE TABLE IF NOT EXISTS mail_send_batches (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    sent_by    BIGINT    NOT NULL,
    sent_at    TIMESTAMP NOT NULL,
    notes      TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sent_by) REFERENCES staffs(id)
);

CREATE TABLE IF NOT EXISTS mail_sends (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT                        NOT NULL,
    office_id  BIGINT                        NOT NULL,
    send_type  ENUM('PLAN','MONITORING')     NOT NULL,
    send_month DATE                          NOT NULL,
    status     ENUM('PENDING','SENT','DONE') NOT NULL DEFAULT 'PENDING',
    batch_id   BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)    REFERENCES users(id),
    FOREIGN KEY (office_id)  REFERENCES offices(id),
    FOREIGN KEY (batch_id)   REFERENCES mail_send_batches(id),
    FOREIGN KEY (created_by) REFERENCES staffs(id)
);
