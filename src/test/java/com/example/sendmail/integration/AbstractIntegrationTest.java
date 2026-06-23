package com.example.sendmail.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 結合テスト（*IT）基底クラス。
 *
 * 本番（Railway MySQL）と同じ MySQL 9.4 を Testcontainers で起動し、
 * @ServiceConnection で DataSource を自動的にコンテナへ向ける。
 *
 * 意図的に @Testcontainers / @Container は付けていない。
 * これらを付けると、JUnit5の拡張がサブクラスごとに beforeAll で start() を
 * 呼び出すため、同じ static フィールドでも呼び出しごとにコンテナが
 * 再作成されてしまい（コンテナID・ポートが変わる）、先に作られたコンテナを
 * 参照していたコネクションが Connection refused になる不具合が確認された。
 * static イニシャライザで明示的に1回だけ start() することで、
 * 複数のサブクラス（テストクラス）間で同一コンテナを安全に共有する
 * （Testcontainers公式の "Singleton container" パターン）。
 */
@SpringBootTest
@ActiveProfiles("it")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:9.4"));

    static {
        MYSQL.start();
    }
}
