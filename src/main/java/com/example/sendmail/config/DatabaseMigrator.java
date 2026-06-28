package com.example.sendmail.config;

import com.example.sendmail.domain.entity.Role;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.repository.RoleRepository;
import com.example.sendmail.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrator implements CommandLineRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final StaffRepository staffRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws InterruptedException {
        waitForDatabase();
        initSchema();
        insertRolesIfNotExists();
        migrateStaffsRoleColumnIfNeeded();
        addAccessibilitySettingsColumnIfNotExists();
        insertAdminIfNotExists();
        addBuildingColumnIfNotExists();
        addOfficeTypeColumnIfNotExists();
        createAccessLogsTableIfNotExists();
        addAssignedStaffColumnIfNotExists();
        addRecipientNumberColumnIfNotExists();
        addDisabilityCategoryColumnIfNotExists();
        createMonitoringCyclesTableIfNotExists();
    }

    private void waitForDatabase() throws InterruptedException {
        for (int i = 1; i <= 30; i++) {
            try {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                log.info("Migration: database connection established (attempt {})", i);
                return;
            } catch (Exception e) {
                log.warn("Migration: DB not ready (attempt {}/30), retrying in 5s...", i);
                Thread.sleep(5000);
            }
        }
        log.error("Migration: could not connect to database after 30 retries");
    }

    private void initSchema() {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("schema-mysql.sql"));
            populator.setContinueOnError(true);
            populator.execute(dataSource);
            log.info("Migration: schema initialized");
        } catch (Exception e) {
            log.warn("Migration: schema init skipped: {}", e.getMessage());
        }
    }

    private void insertRolesIfNotExists() {
        try {
            jdbcTemplate.execute("INSERT IGNORE INTO roles (name) VALUES ('ADMIN'), ('STAFF')");
            log.info("Migration: roles initialized");
        } catch (Exception e) {
            log.warn("Migration: roles init skipped: {}", e.getMessage());
        }
    }

    private void migrateStaffsRoleColumnIfNeeded() {
        try {
            Integer roleIdCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'staffs' AND COLUMN_NAME = 'role_id'",
                    Integer.class);
            if (roleIdCount == null || roleIdCount == 0) {
                jdbcTemplate.execute("ALTER TABLE staffs ADD COLUMN role_id BIGINT NULL");
                jdbcTemplate.execute(
                        "UPDATE staffs s JOIN roles r ON r.name = s.role SET s.role_id = r.id");
                jdbcTemplate.execute("ALTER TABLE staffs MODIFY COLUMN role_id BIGINT NOT NULL");
                jdbcTemplate.execute(
                        "ALTER TABLE staffs ADD CONSTRAINT fk_staffs_role FOREIGN KEY (role_id) REFERENCES roles(id)");
                log.info("Migration: staffs.role_id column added and populated from role ENUM");
            }
            Integer oldRoleCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'staffs' AND COLUMN_NAME = 'role'",
                    Integer.class);
            if (oldRoleCount != null && oldRoleCount > 0) {
                jdbcTemplate.execute("ALTER TABLE staffs DROP COLUMN role");
                log.info("Migration: staffs.role (ENUM) column dropped");
            }
        } catch (Exception e) {
            log.warn("Migration: staffs role column migration skipped: {}", e.getMessage());
        }
    }

    /**
     * 初回起動時のみ初期管理者を作成する。既存の管理者がいる場合は、運用中に
     * パスワードを変更済みの可能性があるため、パスワードには一切手を加えない
     * （以前はハッシュが"changeme"と不一致なら強制的に書き戻していたが、
     * 本番でも毎回このロジックが走るため、変更済みパスワードが意図せず
     * デフォルトへ巻き戻るリスクがあった）。
     */
    private void insertAdminIfNotExists() {
        try {
            var existing = staffRepository.findByEmailIgnoreCase("admin@example.com");
            if (existing.isEmpty()) {
                Role adminRole = roleRepository.findByName("ADMIN")
                        .orElseThrow(() -> new RuntimeException("ADMIN role not found"));
                Staff admin = new Staff();
                admin.setName("管理者");
                admin.setEmail("admin@example.com");
                admin.setPasswordHash(passwordEncoder.encode("changeme"));
                admin.setRole(adminRole);
                admin.setIsActive(true);
                admin.setForcePasswordChange(true);
                staffRepository.save(admin);
                log.info("Migration: initial admin user created");
            } else {
                log.info("Migration: admin user already exists");
            }
        } catch (Exception e) {
            log.warn("Migration: admin user creation skipped: {}", e.getMessage());
        }
    }

    private void addBuildingColumnIfNotExists() {
        addColumnIfNotExists("offices", "building",
                "ALTER TABLE offices ADD COLUMN building VARCHAR(200) NULL AFTER postal_code");
    }

    private void addRecipientNumberColumnIfNotExists() {
        addColumnIfNotExists("users", "recipient_number",
                "ALTER TABLE users ADD COLUMN recipient_number VARCHAR(20) NULL AFTER notes");
    }

    private void addDisabilityCategoryColumnIfNotExists() {
        addColumnIfNotExists("users", "disability_support_category",
                "ALTER TABLE users ADD COLUMN disability_support_category VARCHAR(20) NULL AFTER recipient_number");
    }

    private void createMonitoringCyclesTableIfNotExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'monitoring_cycles'",
                    Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE monitoring_cycles (" +
                    "  id                   BIGINT       AUTO_INCREMENT PRIMARY KEY," +
                    "  user_id              BIGINT       NOT NULL UNIQUE," +
                    "  cycle_months         TINYINT      NOT NULL DEFAULT 6," +
                    "  next_monitoring_date DATE," +
                    "  next_plan_draft_date DATE," +
                    "  next_plan_date       DATE," +
                    "  notes                VARCHAR(500)," +
                    "  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "  CONSTRAINT fk_mc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                    ")");
                log.info("Migration: monitoring_cycles table created");
            }
        } catch (Exception e) {
            log.warn("Migration: monitoring_cycles table creation skipped: {}", e.getMessage());
        }
    }

    private void addAssignedStaffColumnIfNotExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'assigned_staff_id'",
                    Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN assigned_staff_id BIGINT NULL");
                jdbcTemplate.execute(
                    "ALTER TABLE users ADD CONSTRAINT fk_users_assigned_staff " +
                    "FOREIGN KEY (assigned_staff_id) REFERENCES staffs(id) ON DELETE SET NULL");
                log.info("Migration: users.assigned_staff_id column added");
            }
        } catch (Exception e) {
            log.warn("Migration: users.assigned_staff_id column skipped: {}", e.getMessage());
        }
    }

    private void addAccessibilitySettingsColumnIfNotExists() {
        addColumnIfNotExists("staffs", "accessibility_settings",
                "ALTER TABLE staffs ADD COLUMN accessibility_settings TEXT NULL");
    }

    private void addOfficeTypeColumnIfNotExists() {
        addColumnIfNotExists("offices", "office_type",
                "ALTER TABLE offices ADD COLUMN office_type VARCHAR(100) NULL AFTER name");
    }

    private void addColumnIfNotExists(String table, String column, String alterDdl) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column);
            if (count == null || count == 0) {
                jdbcTemplate.execute(alterDdl);
                log.info("Migration: added column {}.{}", table, column);
            }
        } catch (Exception e) {
            log.warn("Migration: {}.{} column skipped: {}", table, column, e.getMessage());
        }
    }

    private void createAccessLogsTableIfNotExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'access_logs'",
                    Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE access_logs (" +
                    "  id            BIGINT       AUTO_INCREMENT PRIMARY KEY," +
                    "  staff_email   VARCHAR(255)," +
                    "  action        VARCHAR(50)  NOT NULL," +
                    "  resource_type VARCHAR(50)," +
                    "  resource_id   BIGINT," +
                    "  details       VARCHAR(500)," +
                    "  ip_address    VARCHAR(45)," +
                    "  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  INDEX idx_access_logs_email    (staff_email)," +
                    "  INDEX idx_access_logs_created  (created_at)," +
                    "  INDEX idx_access_logs_resource (resource_type, resource_id)" +
                    ")");
                log.info("Migration: access_logs table created");
            }
        } catch (Exception e) {
            log.warn("Migration: access_logs table creation skipped: {}", e.getMessage());
        }
    }
}
