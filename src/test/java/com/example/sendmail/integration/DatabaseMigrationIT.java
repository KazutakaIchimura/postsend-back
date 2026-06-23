package com.example.sendmail.integration;

import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.repository.RoleRepository;
import com.example.sendmail.repository.StaffRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DatabaseMigrator が実際の MySQL 上で正しく完走することを確認する結合テスト。
 *
 * 単体テスト（H2）では下記の警告が出ており、本番MySQLでの動作保証になっていなかった:
 *   - ALTER TABLE staffs ADD COLUMN role_id BIGINT NULL
 *   - ALTER TABLE offices ADD COLUMN building VARCHAR(200) NULL AFTER postal_code
 * このテストは @SpringBootTest で実MySQLコンテナ上にアプリ起動時と同じ
 * DatabaseMigrator（schema-mysql.sql 実行 → ロール/管理者初期化 → カラム移行）を
 * 走らせ、その結果（最終状態）が正しいことを検証する。
 */
@DisplayName("DatabaseMigrator が実MySQL上で完走し、初期データが正しく投入される")
class DatabaseMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("roles テーブルに ADMIN / STAFF が投入される")
    void rolesAreSeeded() {
        assertThat(roleRepository.findByName("ADMIN")).isPresent();
        assertThat(roleRepository.findByName("STAFF")).isPresent();
    }

    @Test
    @DisplayName("初期管理者（admin@example.com）が作成され、パスワードは changeme でログインできる状態になる")
    void initialAdminIsCreated() {
        Staff admin = staffRepository.findByEmailIgnoreCase("admin@example.com").orElseThrow();

        assertThat(admin.getRole().getName()).isEqualTo("ADMIN");
        assertThat(admin.getIsActive()).isTrue();
        assertThat(passwordEncoder.matches("changeme", admin.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("staffs テーブルは role_id 列を持ち、旧 role（ENUM）列は存在しない")
    void staffsTableHasRoleIdAndNotLegacyRoleColumn() {
        assertThat(columnExists("staffs", "role_id")).isTrue();
        assertThat(columnExists("staffs", "role")).isFalse();
    }

    @Test
    @DisplayName("offices テーブルは building 列を持つ")
    void officesTableHasBuildingColumn() {
        assertThat(columnExists("offices", "building")).isTrue();
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }
}
