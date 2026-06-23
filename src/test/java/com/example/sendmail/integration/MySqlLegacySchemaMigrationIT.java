package com.example.sendmail.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 では bad SQL grammar として警告が出ていた DatabaseMigrator の
 * MySQL固有 ALTER TABLE 文（{@link com.example.sendmail.config.DatabaseMigrator}
 * の migrateStaffsRoleColumnIfNeeded / addBuildingColumnIfNotExists と同じSQL）が、
 * 実際の MySQL 上では成功することを直接検証する。
 *
 * Spring コンテキストを起動せず、旧スキーマ（role ENUM列のみ・building列なし）を
 * 素のJDBCで再現してからマイグレーションSQLを流すことで、
 * 「カラムが既に存在するため ALTER が実行されずスキップされてしまう」状態を避け、
 * ALTER 文そのものの正当性を検証する。
 */
@Testcontainers
@DisplayName("旧スキーマ → 新スキーマへのMySQL固有ALTER文が実MySQL上で成功する")
class MySqlLegacySchemaMigrationIT {

    @Container
    static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:9.4"));

    @Test
    @DisplayName("staffs.role(ENUM)→role_id への移行、offices への building 列追加（ADD COLUMN ... AFTER）が成功する")
    void legacyAlterStatementsSucceedOnRealMysql() throws SQLException {
        try (Connection conn = mysql.createConnection("");
             Statement stmt = conn.createStatement()) {

            // 旧スキーマを再現
            stmt.execute("""
                    CREATE TABLE roles (
                        id   BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(50) NOT NULL UNIQUE
                    )""");
            stmt.execute("""
                    CREATE TABLE staffs (
                        id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name          VARCHAR(100) NOT NULL,
                        email         VARCHAR(255) NOT NULL UNIQUE,
                        password_hash VARCHAR(255) NOT NULL,
                        role          ENUM('ADMIN','STAFF') NOT NULL
                    )""");
            stmt.execute("""
                    CREATE TABLE offices (
                        id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name        VARCHAR(200) NOT NULL,
                        postal_code VARCHAR(8),
                        address     VARCHAR(500)
                    )""");
            stmt.execute("INSERT INTO roles (name) VALUES ('ADMIN'), ('STAFF')");
            stmt.execute("""
                    INSERT INTO staffs (name, email, password_hash, role)
                    VALUES ('管理者', 'admin@example.com', 'hash', 'ADMIN')""");

            // DatabaseMigrator.migrateStaffsRoleColumnIfNeeded と同一のSQL
            stmt.execute("ALTER TABLE staffs ADD COLUMN role_id BIGINT NULL");
            stmt.execute("UPDATE staffs s JOIN roles r ON r.name = s.role SET s.role_id = r.id");
            stmt.execute("ALTER TABLE staffs MODIFY COLUMN role_id BIGINT NOT NULL");
            stmt.execute("ALTER TABLE staffs ADD CONSTRAINT fk_staffs_role FOREIGN KEY (role_id) REFERENCES roles(id)");
            stmt.execute("ALTER TABLE staffs DROP COLUMN role");

            // DatabaseMigrator.addBuildingColumnIfNotExists と同一のSQL（MySQL固有の AFTER 句）
            stmt.execute("ALTER TABLE offices ADD COLUMN building VARCHAR(200) NULL AFTER postal_code");

            try (var rs = stmt.executeQuery(
                    "SELECT s.role_id, r.name FROM staffs s JOIN roles r ON r.id = s.role_id " +
                    "WHERE s.email = 'admin@example.com'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("name")).isEqualTo("ADMIN");
            }

            try (var rs = stmt.executeQuery(
                    "SELECT COUNT(*) AS cnt FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'staffs' AND COLUMN_NAME = 'role'")) {
                rs.next();
                assertThat(rs.getInt("cnt")).isZero();
            }

            try (var rs = stmt.executeQuery(
                    "SELECT ORDINAL_POSITION FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offices' AND COLUMN_NAME IN ('postal_code', 'building') " +
                    "ORDER BY ORDINAL_POSITION")) {
                rs.next();
                int postalCodePosition = rs.getInt("ORDINAL_POSITION");
                rs.next();
                int buildingPosition = rs.getInt("ORDINAL_POSITION");
                assertThat(buildingPosition).isEqualTo(postalCodePosition + 1);
            }
        }
    }
}
