# Office / MailSend / MailSendBatch / Role API テスト仕様書

## 1. 概要

### テスト対象

`OfficeController`・`MailSendController`・`MailSendBatchController`・`RoleController` が処理する事業所管理・送付レコード管理・一括送付バッチ・ロール参照の各エンドポイント全般。

| コントローラー | テストクラス | テスト件数 | TC 番号 |
|---|---|---|---|
| `OfficeController` | `src/test/java/com/example/sendmail/controller/OfficeControllerTest.java` | 24件 | TC-OFFICE-01 〜 24 |
| `MailSendController` | `src/test/java/com/example/sendmail/controller/MailSendControllerTest.java` | 23件 | TC-MAILSEND-01 〜 23 |
| `MailSendBatchController` | `src/test/java/com/example/sendmail/controller/MailSendBatchControllerTest.java` | 11件 | TC-BATCH-01 〜 11 |
| `RoleController` | `src/test/java/com/example/sendmail/controller/RoleControllerTest.java` | 4件 | TC-ROLE-01 〜 04 |

| 関連プロダクションコード | パス |
|---|---|
| コントローラー | `src/main/java/com/example/sendmail/controller/OfficeController.java` |
| コントローラー | `src/main/java/com/example/sendmail/controller/MailSendController.java` |
| コントローラー | `src/main/java/com/example/sendmail/controller/MailSendBatchController.java` |
| コントローラー | `src/main/java/com/example/sendmail/controller/RoleController.java` |
| サービス | `src/main/java/com/example/sendmail/service/OfficeService.java` |
| サービス | `src/main/java/com/example/sendmail/service/MailSendService.java` |
| サービス | `src/main/java/com/example/sendmail/service/MailSendBatchService.java` |
| リポジトリ | `src/main/java/com/example/sendmail/repository/RoleRepository.java` |
| リクエスト DTO | `CreateOfficeRequest` / `CreateMailSendRequest` / `CreateMailSendBatchRequest` |
| レスポンス DTO | `OfficeResponse` / `MailSendResponse` / `MailSendByOfficeResponse` / `MailSendBatchResponse` / `Role` |
| セキュリティ設定 | `src/main/java/com/example/sendmail/config/SecurityConfig.java` |
| 例外ハンドラー | `src/main/java/com/example/sendmail/exception/GlobalExceptionHandler.java` |

### 目的

- 事業所管理・送付レコード管理・一括送付バッチ・ロール参照の各 API が正しい HTTP ステータスとレスポンスボディを返すことを保証する
- `/api/offices/**`・`/api/mail-sends/**`・`/api/mail-send-batches/**`・`/api/roles/**` が `authenticated()` のみ（ADMIN/STAFF どちらの認証済みユーザーでもアクセス可能、URL ベースのロール制限なし）であることを確認する
- 未認証（401）によるアクセス拒否が全エンドポイントで適切に行われることを確認する
- バリデーションエラー（`@NotBlank`・`@NotNull`・`@NotEmpty`・`@Size`・列挙値不正）の挙動を仕様として記録する
- 異常系（リソース不存在 404、重複 409、ステータス不整合 409）の挙動を仕様として記録する
- `MailSendResponse.sendMonth` のシリアライズに関する仕様上の疑問点（後述）を記録する

### 実行方法

```bash
# プロジェクトルートで実行
./mvnw test -Dtest=OfficeControllerTest
./mvnw test -Dtest=MailSendControllerTest
./mvnw test -Dtest=MailSendBatchControllerTest
./mvnw test -Dtest=RoleControllerTest

# 特定のネストクラスのみ実行
./mvnw test -Dtest="OfficeControllerTest\$CreateOffice"

# 特定のテストケースのみ実行
./mvnw test -Dtest="MailSendControllerTest#should_return201_when_creatingValidMailSend"
```

---

## 2. テスト環境

### 使用技術・フレームワーク

| 項目 | 内容 |
|---|---|
| Spring Boot | 4.0.6（Spring Framework 7.0） |
| Java | 21 |
| テストフレームワーク | JUnit 5（JUnit Jupiter） |
| MockMvc | `@SpringBootTest` + `@AutoConfigureMockMvc`（統合テスト） |
| モックライブラリ | Mockito（`@MockitoBean`） |
| テスト用 DB | H2 インメモリ（MySQL モード） |

### テストクラスのアノテーション構成（4 クラス共通）

```java
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("XxxController 統合テスト")
class XxxControllerTest { ... }
```

`@SpringBootTest` により実際の `SecurityFilterChain` を起動し、認証・認可フロー全体を通したテストを実現している。

### モック方針

| コンポーネント | コントローラー | 方針 | 理由 |
|---|---|---|---|
| `OfficeService` | OfficeController | `@MockitoBean` でモック | DB 接続なしにインメモリで完結させる |
| `MailSendService` | MailSendController | `@MockitoBean` でモック | 同上 |
| `MailSendBatchService` | MailSendBatchController | `@MockitoBean` でモック | 同上 |
| `RoleRepository` | RoleController | `@MockitoBean` でモック | 同上（サービス層を介さず直接リポジトリを参照する設計） |
| Spring Security 認証状態 | 全クラス | `@WithMockUser` で表現 | 実際のログインフローなしに認証済み状態を簡潔に設定できる |
| `DatabaseMigrator` | 全クラス | モックせず起動（エラー無視） | `setContinueOnError=true` で H2 上の MySQL 固有構文エラーを無視 |

### 認証ユーザー名の使い分け

- `OfficeControllerTest` / `RoleControllerTest`: `@WithMockUser(roles = "ADMIN" または "STAFF")`（username はデフォルト）
- `MailSendControllerTest` / `MailSendBatchControllerTest`: `@WithMockUser(username = "tanaka@example.com", roles = "STAFF")` を使用し、`Authentication.getName()` がそのままサービス層の `staffEmail` 引数として渡される実装を検証している（`eq(STAFF_EMAIL)` で照合）

### 共通テストフィクスチャ概要（`@BeforeEach`）

| クラス | フィクスチャ変数 | 概要 |
|---|---|---|
| OfficeControllerTest | `activeOfficeResponse` / `inactiveOfficeResponse` | id=1 中央事業所（active）/ id=2 廃止事業所（inactive、building=null） |
| MailSendControllerTest | `pendingResponse` / `sentResponse` | id=1 PLAN/PENDING（batchId=null）/ id=2 MONITORING/SENT（batchId=50） |
| MailSendBatchControllerTest | `batchResponse` | batchId=1, updatedCount=3, notes="6月分発送" |
| RoleControllerTest | `adminRole` / `staffRole` | id=1 ADMIN / id=2 STAFF |

---

## 3. OfficeController テストケース一覧（`/api/offices`）

セキュリティ: `authenticated()` のみ（ADMIN/STAFF どちらも可、ロール制限なし）

| TC 番号 | エンドポイント | HTTP | シナリオ | 前提条件 | 期待ステータス |
|---|---|---|---|---|---|
| TC-OFFICE-01 | `/api/offices` | GET | 認証済み（STAFF）でアクティブ事業所一覧取得 | `@WithMockUser(roles="STAFF")` | 200 OK |
| TC-OFFICE-02 | `/api/offices?includeInactive=true` | GET | 認証済み（ADMIN）で全事業所取得（非アクティブ含む） | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-OFFICE-03 | `/api/offices` | GET | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-OFFICE-04 | `/api/offices` | GET | 事業所が0件 | `@WithMockUser(roles="STAFF")`、listOffices=[] | 200 OK（空配列） |
| TC-OFFICE-05 | `/api/offices` | POST | 全フィールド指定で作成 | `@WithMockUser(roles="ADMIN")` | 201 Created |
| TC-OFFICE-06 | `/api/offices` | POST | name が blank | `@WithMockUser(roles="ADMIN")` | 400 Bad Request（`@NotBlank`） |
| TC-OFFICE-07 | `/api/offices` | POST | name が省略 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request（`@NotBlank`） |
| TC-OFFICE-08 | `/api/offices` | POST | name が201文字（max=200違反） | `@WithMockUser(roles="ADMIN")` | 400 Bad Request（`@Size`） |
| TC-OFFICE-09 | `/api/offices` | POST | postalCode が9文字（max=8違反） | `@WithMockUser(roles="ADMIN")` | 400 Bad Request（`@Size`） |
| TC-OFFICE-10 | `/api/offices` | POST | 任意項目（postalCode等）省略 | `@WithMockUser(roles="ADMIN")` | 201 Created |
| TC-OFFICE-11 | `/api/offices` | POST | 未認証で作成試行 | セッションなし | 401 Unauthorized |
| TC-OFFICE-12 | `/api/offices/{id}` | GET | 存在するIDで詳細取得 | `@WithMockUser(roles="STAFF")` | 200 OK |
| TC-OFFICE-13 | `/api/offices/{id}` | GET | 存在しないID | `@WithMockUser(roles="STAFF")` | 404 Not Found |
| TC-OFFICE-14 | `/api/offices/{id}` | GET | 未認証で実行 | セッションなし | 401 Unauthorized |
| TC-OFFICE-15 | `/api/offices/{id}` | PUT | 有効なリクエストで更新 | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-OFFICE-16 | `/api/offices/{id}` | PUT | name が blank | `@WithMockUser(roles="ADMIN")` | 400 Bad Request（`@NotBlank`） |
| TC-OFFICE-17 | `/api/offices/{id}` | PUT | 存在しないID | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-OFFICE-18 | `/api/offices/{id}` | PUT | 未認証で実行 | セッションなし | 401 Unauthorized |
| TC-OFFICE-19 | `/api/offices/{id}` | DELETE | 事業所を無効化（論理削除） | `@WithMockUser(roles="ADMIN")` | 204 No Content |
| TC-OFFICE-20 | `/api/offices/{id}` | DELETE | 存在しないID | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-OFFICE-21 | `/api/offices/{id}` | DELETE | 未認証で実行 | セッションなし | 401 Unauthorized |
| TC-OFFICE-22 | `/api/offices/{id}/activate` | PATCH | 事業所を有効化 | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-OFFICE-23 | `/api/offices/{id}/activate` | PATCH | 存在しないID | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-OFFICE-24 | `/api/offices/{id}/activate` | PATCH | 未認証で実行 | セッションなし | 401 Unauthorized |

### 3.1 テストケース詳細（補足事項のあるもののみ）

#### TC-OFFICE-01 / TC-OFFICE-12: ロール制限なしの確認

`/api/offices/**` は `SecurityConfig` 上 `authenticated()` のみで ADMIN/STAFF どちらでもアクセス可能。一覧取得は `@WithMockUser(roles="STAFF")`、更新系は `@WithMockUser(roles="ADMIN")` というように、テスト全体としてどちらのロールでも 200 系が返ることをカバーしている（明示的な 403 ロール拒否テストは存在しない＝ロール制限がないことの裏返し）。

- **対応テストメソッド**: `should_return200_when_listingActiveOffices`

#### TC-OFFICE-08: name の最大長バリデーション（境界値）

`"あ".repeat(201)` で日本語 201 文字を生成し `@Size(max=200)` 違反を検証。マルチバイト文字の境界値テストとして設計されている。

- **検証項目**: ステータス `400 Bad Request`、`$.message == "入力値が不正です"`、`$.errors.name` が存在
- **対応テストメソッド**: `should_return400_when_nameIsTooLong`

#### TC-OFFICE-10: 任意項目省略時の正常系

`name` のみを指定したリクエストでも 201 Created が返ることを確認し、`postalCode`・`building`・`address`・`phone` が任意項目（バリデーションアノテーションなし、または許容される）であることを記録する。

- **対応テストメソッド**: `should_return201_when_optionalFieldsAreOmitted`

#### TC-OFFICE-19 / TC-OFFICE-22: 論理削除と有効化のペア

DELETE は `officeService.deactivateOffice(id)` を呼び出し 204 No Content（レスポンスボディなし）、PATCH `/activate` は `officeService.activateOffice(id)` を呼び出し 200 OK + `OfficeResponse`（`isActive=true`）を返す非対称な設計になっている点に注意。

- **対応テストメソッド**: `should_return204_when_deactivatingOffice` / `should_return200_when_activatingOffice`

---

## 4. MailSendController テストケース一覧（`/api/mail-sends`）

セキュリティ: `authenticated()` のみ（ADMIN/STAFF どちらも可、ロール制限なし）。認証ユーザー名は `tanaka@example.com`（`STAFF_EMAIL` 定数）を使用し `Authentication.getName()` がサービスへ伝播することを検証する。

| TC 番号 | エンドポイント | HTTP | シナリオ | 前提条件 | 期待ステータス |
|---|---|---|---|---|---|
| TC-MAILSEND-01 | `/api/mail-sends` | GET | 認証済みユーザーが一覧取得 | `@WithMockUser(username=STAFF_EMAIL, roles="STAFF")` | 200 OK |
| TC-MAILSEND-02 | `/api/mail-sends` | GET | レコードが0件 | 同上、listMailSends=[] | 200 OK（空配列） |
| TC-MAILSEND-03 | `/api/mail-sends` | GET | 未認証でアクセス | セッションなし | 401 Unauthorized |
| TC-MAILSEND-04 | `/api/mail-sends/by-office` | GET | 事業所別グルーピング一覧取得 | `@WithMockUser(username=STAFF_EMAIL, roles="STAFF")` | 200 OK |
| TC-MAILSEND-05 | `/api/mail-sends/by-office` | GET | レコードが0件 | 同上、listByOffice=[] | 200 OK（空配列） |
| TC-MAILSEND-06 | `/api/mail-sends/by-office` | GET | 未認証でアクセス | セッションなし | 401 Unauthorized |
| TC-MAILSEND-07 | `/api/mail-sends` | POST | 有効なリクエストで作成（`Authentication.getName()`伝播） | `@WithMockUser(username=STAFF_EMAIL, roles="STAFF")` | 201 Created |
| TC-MAILSEND-08 | `/api/mail-sends` | POST | userId が null | 同上 | 400 Bad Request（`@NotNull`） |
| TC-MAILSEND-09 | `/api/mail-sends` | POST | officeId が null | 同上 | 400 Bad Request（`@NotNull`） |
| TC-MAILSEND-10 | `/api/mail-sends` | POST | sendType が不正な列挙値 | 同上 | 400 Bad Request（リクエスト形式不正） |
| TC-MAILSEND-11 | `/api/mail-sends` | POST | sendMonth が省略 | 同上 | 400 Bad Request（`@NotNull`） |
| TC-MAILSEND-12 | `/api/mail-sends` | POST | 利用者が見つからない | 同上 | 404 Not Found |
| TC-MAILSEND-13 | `/api/mail-sends` | POST | 重複する送付レコード | 同上 | 409 Conflict |
| TC-MAILSEND-14 | `/api/mail-sends` | POST | 未認証で実行 | セッションなし | 401 Unauthorized |
| TC-MAILSEND-15 | `/api/mail-sends/{id}` | PUT | 有効なリクエストで更新 | `@WithMockUser(username=STAFF_EMAIL, roles="STAFF")` | 200 OK |
| TC-MAILSEND-16 | `/api/mail-sends/{id}` | PUT | sendType が省略 | 同上 | 400 Bad Request（`@NotNull`） |
| TC-MAILSEND-17 | `/api/mail-sends/{id}` | PUT | 存在しないID | 同上 | 404 Not Found |
| TC-MAILSEND-18 | `/api/mail-sends/{id}` | PUT | PENDING以外のレコードを更新 | 同上 | 409 Conflict（`InvalidStatusException`） |
| TC-MAILSEND-19 | `/api/mail-sends/{id}` | PUT | 未認証で実行 | セッションなし | 401 Unauthorized |
| TC-MAILSEND-20 | `/api/mail-sends/{id}` | DELETE | レコードを削除 | `@WithMockUser(username=STAFF_EMAIL, roles="STAFF")` | 204 No Content |
| TC-MAILSEND-21 | `/api/mail-sends/{id}` | DELETE | 存在しないID | 同上 | 404 Not Found |
| TC-MAILSEND-22 | `/api/mail-sends/{id}` | DELETE | PENDING以外のレコードを削除 | 同上 | 409 Conflict（`InvalidStatusException`） |
| TC-MAILSEND-23 | `/api/mail-sends/{id}` | DELETE | 未認証で実行 | セッションなし | 401 Unauthorized |

### 4.1 テストケース詳細（補足事項のあるもののみ）

#### TC-MAILSEND-07: `Authentication.getName()` のサービス層への伝播

`@WithMockUser(username = "tanaka@example.com", ...)` で認証したユーザー名が、コントローラーから `mailSendService.createMailSend(request, "tanaka@example.com")` の第二引数としてそのまま渡されることを `eq(STAFF_EMAIL)` と `verify()` で検証している。これは MailSend / MailSendBatch 両コントローラーに共通する設計で、認証ユーザー＝操作者として記録される業務要件を反映している。

- **対応テストメソッド**: `should_return201_when_creatingValidMailSend`

#### TC-MAILSEND-10: 不正な列挙値リクエスト

`sendType: "INVALID_TYPE"` のように `SendType` enum に存在しない文字列を渡すと、Bean Validation ではなく JSON デシリアライズ段階でエラーとなり、`GlobalExceptionHandler` が「リクエストの形式が不正です」というメッセージで 400 を返す。`@NotNull` 系バリデーションエラー（メッセージ「入力値が不正です」+ `errors.<field>`）とはエラーレスポンスの形式が異なる点に注意。

- **検証項目**: ステータス `400 Bad Request`、`$.message == "リクエストの形式が不正です"`（`errors` フィールドなし）
- **対応テストメソッド**: `should_return400_when_sendTypeIsInvalid`

#### TC-MAILSEND-15: `sendMonth` シリアライズに関する仕様メモ（Issue #54で修正済み）

`MailSendResponse.sendMonth` は `@JsonSerialize(using = YearMonthSerializer.class)` により、レスポンスでは `"yyyy-MM"` 形式（例: `"2026-07"`）でシリアライズされる。

> 旧版（Issue #54対応前）では、Spring Boot 4 がデフォルトで Jackson 3系（`tools.jackson.*`）を使用するのに対し、`MailSendResponse`／`YearMonthSerializer` には Jackson 2系（`com.fasterxml.jackson.databind.*`）の `@JsonSerialize`／`JsonSerializer` が付与されていたため、ランタイムのObjectMapperにアノテーションが認識されず ISO 形式 `"yyyy-MM-dd"` のまま出力されるバグがあった（Lombokのゲッター起因という説は誤りと判明）。Jackson 3系（`tools.jackson.databind.*`）のAPIへ置き換えることで解消した。

- **検証項目**: `$.sendMonth == "2026-07"`（`yyyy-MM` 形式で出力されることを確認）
- **対応テストメソッド**: `should_return200_when_updatingValidMailSend`

#### TC-MAILSEND-18 / TC-MAILSEND-22: ステータス不整合エラー（`InvalidStatusException` → 409）

PENDING 以外（例: SENT）のレコードを更新・削除しようとすると `InvalidStatusException` がスローされ、`GlobalExceptionHandler` により 409 Conflict に変換される。`DuplicateResourceException`（重複）と同じ 409 を使うが例外クラスが異なる点に注意。

- **対応テストメソッド**: `should_return409_when_statusIsNotPending`（PUT・DELETE それぞれに存在）

---

## 5. MailSendBatchController テストケース一覧（`/api/mail-send-batches`）

セキュリティ: `authenticated()` のみ（ADMIN/STAFF どちらも可、ロール制限なし）。認証ユーザー名は `tanaka@example.com`（`STAFF_EMAIL` 定数）を使用。

| TC 番号 | エンドポイント | HTTP | シナリオ | 前提条件 | 期待ステータス |
|---|---|---|---|---|---|
| TC-BATCH-01 | `/api/mail-send-batches` | POST | 有効なリクエストでバッチ作成（`Authentication.getName()`伝播） | `@WithMockUser(username=STAFF_EMAIL, roles="STAFF")` | 201 Created |
| TC-BATCH-02 | `/api/mail-send-batches` | POST | notes を省略（任意項目） | 同上 | 201 Created |
| TC-BATCH-03 | `/api/mail-send-batches` | POST | mailSendIds が空配列 | 同上 | 400 Bad Request（`@NotEmpty`） |
| TC-BATCH-04 | `/api/mail-send-batches` | POST | mailSendIds が省略 | 同上 | 400 Bad Request（`@NotEmpty`） |
| TC-BATCH-05 | `/api/mail-send-batches` | POST | 存在しない送付レコードIDを含む | 同上 | 404 Not Found |
| TC-BATCH-06 | `/api/mail-send-batches` | POST | PENDING以外のレコードを含む | 同上 | 409 Conflict（`InvalidStatusException`） |
| TC-BATCH-07 | `/api/mail-send-batches` | POST | スタッフが見つからない（認証ユーザーのメールに対応するスタッフ不在） | 同上 | 404 Not Found |
| TC-BATCH-08 | `/api/mail-send-batches` | POST | 未認証で実行 | セッションなし | 401 Unauthorized |
| TC-BATCH-09 | `/api/mail-send-batches/{id}` | GET | 存在するIDでバッチ詳細取得 | `@WithMockUser(username=STAFF_EMAIL, roles="STAFF")` | 200 OK |
| TC-BATCH-10 | `/api/mail-send-batches/{id}` | GET | 存在しないID | 同上 | 404 Not Found |
| TC-BATCH-11 | `/api/mail-send-batches/{id}` | GET | 未認証で実行 | セッションなし | 401 Unauthorized |

### 5.1 テストケース詳細（補足事項のあるもののみ）

#### TC-BATCH-01: バッチ作成と `Authentication.getName()` 伝播

`mailSendBatchService.createBatch(request, "tanaka@example.com")` への伝播を `eq(STAFF_EMAIL)` + `verify()` で検証。レスポンスは `batchId`・`updatedCount`・`notes`・`sentAt` を含む `MailSendBatchResponse`。

- **検証項目**: ステータス `201 Created`、`$.batchId == 1`、`$.updatedCount == 3`、`$.notes == "6月分発送"`
- **対応テストメソッド**: `should_return201_when_creatingValidBatch`

#### TC-BATCH-05 vs TC-BATCH-07: 404 の発生要因の違い

どちらも `ResourceNotFoundException` による 404 だが、メッセージで原因が区別される。

- TC-BATCH-05: 「存在しない送付レコードが含まれています」（`mailSendIds` 内に不正な ID）
- TC-BATCH-07: 「スタッフが見つかりません: tanaka@example.com」（認証ユーザーのメールに紐づく `Staff` エンティティが存在しない）

サービス層が認証ユーザーのメールアドレスから操作者スタッフを解決する実装になっており、スタッフが未登録の場合のエッジケースとして TC-BATCH-07 が設計されている。

- **対応テストメソッド**: `should_return404_when_mailSendIdNotFound` / `should_return404_when_staffNotFound`

#### TC-BATCH-03 / TC-BATCH-04: `mailSendIds` の必須＆非空バリデーション

`@NotEmpty` により、フィールド省略（null）と空配列 `[]` の両方が 400 Bad Request + `$.errors.mailSendIds` を返すことを個別にテストし、両方のケースで同一バリデーションが機能することを保証している。

- **対応テストメソッド**: `should_return400_when_mailSendIdsIsEmpty` / `should_return400_when_mailSendIdsIsMissing`

---

## 6. RoleController テストケース一覧（`/api/roles`）

セキュリティ: `authenticated()` のみ（ADMIN/STAFF どちらも可、ロール制限なし）。`RoleService` を介さず `RoleRepository` を直接 `@MockitoBean` でモックしている点が他コントローラーと異なる。

| TC 番号 | エンドポイント | HTTP | シナリオ | 前提条件 | 期待ステータス |
|---|---|---|---|---|---|
| TC-ROLE-01 | `/api/roles` | GET | 認証済み（ADMIN）でロール一覧取得 | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-ROLE-02 | `/api/roles` | GET | 認証済み（STAFF）でロール一覧取得（ロール制限なしの確認） | `@WithMockUser(roles="STAFF")` | 200 OK |
| TC-ROLE-03 | `/api/roles` | GET | 未認証でアクセス | セッションなし | 401 Unauthorized |
| TC-ROLE-04 | `/api/roles` | GET | ロールが0件 | `@WithMockUser(roles="ADMIN")`、findAll=[] | 200 OK（空配列） |

### 6.1 テストケース詳細（補足事項のあるもののみ）

#### TC-ROLE-01 / TC-ROLE-02: ADMIN・STAFF どちらでもアクセス可能であることの確認ペア

同一エンドポイント・同一スタブ設定（`roleRepository.findAll()` が `[adminRole, staffRole]` を返す）に対し、`roles="ADMIN"` と `roles="STAFF"` の両方で 200 OK が返ることを確認することで、`/api/roles` に URL ベースのロール制限が存在しないことを実証している。TC-ROLE-02 は検証項目を `$.length()` のみに絞り、ロール差異がないことの確認に焦点を当てている。

- **対応テストメソッド**: `should_return200_when_adminListsRoles` / `should_return200_when_staffListsRoles`

#### TC-ROLE-01: レスポンス構造

`Role` エンティティを直接シリアライズして返す設計（専用の Response DTO は使用しない）。レスポンスは `[{ "id": 1, "name": "ADMIN" }, { "id": 2, "name": "STAFF" }]` の形式。

- **検証項目**: `$[0].id == 1`、`$[0].name == "ADMIN"`、`$[1].id == 2`、`$[1].name == "STAFF"`
- **対応テストメソッド**: `should_return200_when_adminListsRoles`

---

## 7. テストケース総括

| コントローラー | 総数 | 正常系(2xx) | バリデーション(400) | 認証(401) | 認可(403) | 不存在(404) | 競合(409) |
|---|---|---|---|---|---|---|---|
| OfficeController | 24 | 9 | 4 | 6 | 0 | 5 | 0 |
| MailSendController | 23 | 6 | 5 | 6 | 0 | 4 | 4 |
| MailSendBatchController | 11 | 2 | 2 | 3 | 0 | 3 | 1 |
| RoleController | 4 | 3 | 0 | 1 | 0 | 0 | 0 |
| **合計** | **62** | **20** | **11** | **16** | **0** | **12** | **5** |

> 4 コントローラーともに `/api/**` セキュリティ設定が `authenticated()` のみ（URL ベースのロール制限なし）であるため、`StaffController`（ADMIN 限定）のような 403 Forbidden ロール拒否テストは存在しない。これは仕様どおりであり、欠落ではない。

---

## 8. 発見された注意点・仕様メモ

| 項目 | 内容 | 該当箇所 |
|---|---|---|
| `sendMonth` シリアライズ不整合（Issue #54で修正済み） | Spring Boot 4 のデフォルトJSONエンジンが Jackson 3系（`tools.jackson.*`）であるのに対し、`MailSendResponse`／`YearMonthSerializer` には Jackson 2系（`com.fasterxml.jackson.databind.*`）の `@JsonSerialize`／`JsonSerializer` が付与されており、ランタイムで認識されず ISO 形式 `"yyyy-MM-dd"` のまま出力されていた（Lombok起因という説は誤りと判明）。Jackson 3系のAPIへ置き換え、`"yyyy-MM"` 形式で出力されるよう修正済み。 | TC-MAILSEND-15（`should_return200_when_updatingValidMailSend`） |
| 列挙値不正のエラー形式の違い | `@NotNull` 等の Bean Validation エラーは `{"message": "入力値が不正です", "errors": {...}}` 形式だが、JSON デシリアライズ段階でのenum不正値エラーは `{"message": "リクエストの形式が不正です"}` 形式（`errors` フィールドなし）。フロントエンド側でエラー表示を実装する際はこの違いに注意が必要。 | TC-MAILSEND-10 |
| `RoleController` の薄い実装 | `RoleService` を介さず `RoleRepository.findAll()` を直接呼び出す設計。他コントローラーがサービス層を経由するのと対照的で、ロール一覧が単純な参照データであることを反映している。 | RoleControllerTest 全体 |
