-- 開発・デモ用テストデータ投入スクリプト
-- 既存データを全クリアし、画面確認用の一連のテストデータを投入する。
-- roles テーブルは固定値（ADMIN/STAFF）のため対象外。

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE mail_sends;
TRUNCATE TABLE mail_send_batches;
TRUNCATE TABLE user_offices;
TRUNCATE TABLE users;
TRUNCATE TABLE offices;
TRUNCATE TABLE staffs;
SET FOREIGN_KEY_CHECKS = 1;

-- staffs（管理者1名・職員2名）。password は全員 "changeme" の BCrypt ハッシュ
INSERT INTO staffs (id, name, email, password_hash, role_id, is_active, force_password_change) VALUES
  (1, '管理者', 'admin@example.com', '$2a$10$torMNzg4NWnnrY3icoqHK.38U/cvckBswiQHj5APrDLzRFkJAIZ2G', (SELECT id FROM roles WHERE name = 'ADMIN'), TRUE, TRUE),
  (2, '佐藤 由美', 'sato@example.com', '$2a$10$torMNzg4NWnnrY3icoqHK.38U/cvckBswiQHj5APrDLzRFkJAIZ2G', (SELECT id FROM roles WHERE name = 'STAFF'), TRUE, TRUE),
  (3, '鈴木 健太', 'suzuki.staff@example.com', '$2a$10$torMNzg4NWnnrY3icoqHK.38U/cvckBswiQHj5APrDLzRFkJAIZ2G', (SELECT id FROM roles WHERE name = 'STAFF'), TRUE, TRUE);

-- offices（事業所4件）
INSERT INTO offices (id, name, postal_code, building, address, phone, is_active) VALUES
  (1, 'グループホームさくら', '160-0023', NULL, '東京都新宿区西新宿1-2-3', '03-1111-2222', TRUE),
  (2, '就労継続支援B型 ひまわり', '150-0002', 'ひまわりビル3F', '東京都渋谷区渋谷2-3-4', '03-3333-4444', TRUE),
  (3, '生活介護センター きずな', '220-0011', NULL, '神奈川県横浜市西区高島3-4-5', '045-5555-6666', TRUE),
  (4, '相談支援事業所 ゆい', '231-0023', 'ゆいビル2F', '神奈川県横浜市中区山下町4-5-6', '045-7777-8888', TRUE);

-- users（利用者6名。1名は無効化済みデータの確認用に is_active = FALSE）
INSERT INTO users (id, name, name_kana, birth_date, notes, is_active) VALUES
  (1, '田中 太郎', 'たなか たろう', '1985-04-12', '車椅子使用。送付先が2事業所あるケース', TRUE),
  (2, '山田 花子', 'やまだ はなこ', '1990-08-23', NULL, TRUE),
  (3, '鈴木 一郎', 'すずき いちろう', '1978-11-05', '送付遅延（overdue）確認用', TRUE),
  (4, '高橋 美咲', 'たかはし みさき', '2000-02-14', '送付済み（SENT）確認用', TRUE),
  (5, '渡辺 健一', 'わたなべ けんいち', '1995-06-30', '複数事業所・複数ステータス混在', TRUE),
  (6, '伊藤 さくら', 'いとう さくら', '1988-09-09', '退所済みのため無効化', FALSE);

-- user_offices（利用者と事業所の紐付け。田中太郎・渡辺健一は複数事業所に紐付け）
INSERT INTO user_offices (user_id, office_id) VALUES
  (1, 1), (1, 2),
  (2, 3),
  (3, 1),
  (4, 2), (4, 4),
  (5, 3), (5, 1),
  (6, 2);

-- mail_send_batches（送付済み一括処理の履歴2件）
INSERT INTO mail_send_batches (id, sent_by, sent_at, notes) VALUES
  (1, 1, '2026-06-15 10:00:00', '6月分定期送付'),
  (2, 2, '2026-03-20 09:30:00', '3月分送付（高橋様分）');

-- mail_sends（送付レコード8件：当月分PENDING・遅延中PENDING・送付済みSENTを混在させる）
INSERT INTO mail_sends (user_id, office_id, send_type, send_month, status, batch_id, created_by) VALUES
  (1, 1, 'PLAN',       '2026-06-01', 'PENDING', NULL, 1),  -- 田中: 当月・未送付（遅延なし）
  (1, 2, 'MONITORING', '2026-06-01', 'PENDING', NULL, 1),  -- 田中: 当月・未送付（遅延なし）
  (2, 3, 'PLAN',       '2026-04-01', 'PENDING', NULL, 1),  -- 山田: 4月分が未送付のまま → 遅延
  (3, 1, 'MONITORING', '2026-05-01', 'PENDING', NULL, 2),  -- 鈴木: 5月分が未送付のまま → 遅延
  (4, 2, 'PLAN',       '2026-06-01', 'SENT',    1,    1),  -- 高橋: 6月分送付済み（batch1）
  (4, 4, 'MONITORING', '2026-06-01', 'SENT',    1,    1),  -- 高橋: 6月分送付済み（batch1）
  (5, 3, 'PLAN',       '2026-03-01', 'SENT',    2,    2),  -- 渡辺: 3月分送付済み（batch2）
  (5, 1, 'MONITORING', '2026-06-01', 'PENDING', NULL, 1);  -- 渡辺: 当月・未送付（遅延なし）

-- 次回 INSERT 時の ID 衝突を避けるため AUTO_INCREMENT を投入済み最大値+1 に合わせる
ALTER TABLE staffs AUTO_INCREMENT = 4;
ALTER TABLE offices AUTO_INCREMENT = 5;
ALTER TABLE users AUTO_INCREMENT = 7;
ALTER TABLE mail_send_batches AUTO_INCREMENT = 3;
