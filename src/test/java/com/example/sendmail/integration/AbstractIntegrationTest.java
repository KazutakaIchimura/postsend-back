package com.example.sendmail.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 結合テスト（*IT）基底クラス。
 *
 * 本番（Railway MySQL）と同じ MySQL 8.0 を Testcontainers で起動し、
 * @ServiceConnection で DataSource を自動的にコンテナへ向ける。
 * コンテナは static フィールドのためサブクラス間で1個を共有し、
 * 起動オーバーヘッドをテストクラス単位ではなく JVM 単位に抑える。
 */
@SpringBootTest
@ActiveProfiles("it")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0"));
}
