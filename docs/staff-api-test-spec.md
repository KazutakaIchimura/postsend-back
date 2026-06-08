# Staff API テスト仕様書

## 1. 概要

### テスト対象

`StaffController` が処理するスタッフ管理エンドポイント全般。

| 対象ファイル | パス |
|---|---|
| テストクラス | `src/test/java/com/example/sendmail/controller/StaffControllerTest.java` |
| プロダクションコード | `src/main/java/com/example/sendmail/controller/StaffController.java` |
| サービス | `src/main/java/com/example/sendmail/service/StaffService.java` |
| リクエスト DTO | `src/main/java/com/example/sendmail/dto/request/CreateStaffRequest.java` |
| リクエスト DTO | `src/main/java/com/example/sendmail/dto/request/UpdateStaffRequest.java` |
| レスポンス DTO | `src/main/java/com/example/sendmail/dto/response/StaffResponse.java` |
| セキュリティ設定 | `src/main/java/com/example/sendmail/config/SecurityConfig.java` |
| 例外ハンドラー | `src/main/java/com/example/sendmail/exception/GlobalExceptionHandler.java` |

### 目的

- スタッフ管理 API の全エンドポイントが正しい HTTP ステータスとレスポンスボディを返すことを保証する
- `/api/staffs/**` が URL ベースのセキュリティ制御により ADMIN ロールのみに制限されていることを確認する
- 未認証（401）・STAFF ロール（403）による不正アクセスが適切に拒否されることを確認する
- `UpdateStaffRequest.password` の省略可能設計（`@NotBlank` なし・`@Size(min=8)` のみ）の挙動を仕様として記録する
- 異常系（バリデーションエラー・リソース不存在・重複メール・自己無効化）の挙動を仕様として記録する

### 実行方法

```bash
# プロジェクトルートで実行
./mvnw test -Dtest=StaffControllerTest

# 特定のネストクラスのみ実行
./mvnw test -Dtest="StaffControllerTest\$CreateStaff"

# 特定のテストケースのみ実行
./mvnw test -Dtest="StaffControllerTest#should_return201_when_adminCreatesValidStaff"
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

### テストクラスのアノテーション構成

```java
@SpringBootTest
@AutoConfigureMockMvc  // org.springframework.boot.webmvc.test.autoconfigure
@DisplayName("StaffController 統合テスト")
class StaffControllerTest { ... }
```

`@SpringBootTest` により実際の `SecurityFilterChain` を起動し、URL ベースのセキュリティ制限・認証エントリーポイントを含む認証・認可フロー全体を通したテストを実現している。

### モック方針

| コンポーネント | 方針 | 理由 |
|---|---|---|
| `StaffService` | `@MockitoBean` でモック | DB 接続なしにインメモリで完結させる |
| Spring Security 認証状態 | `@WithMockUser` で表現 | 実際のログインフローなしに認証済み状態を簡潔に設定できる |
| `DatabaseMigrator` | モックせず起動（エラー無視） | `setContinueOnError=true` で H2 上の MySQL 固有構文エラーを無視 |

### テストフィクスチャ

`@BeforeEach` で以下のレスポンスオブジェクトを初期化する。

| 変数 | id | name | email | role | isActive | forcePasswordChange | 用途 |
|---|---|---|---|---|---|---|---|
| `activeStaffResponse` | 1 | 田中一郎 | tanaka@example.com | ADMIN | true | true | 正常系 GET/POST/PUT レスポンス |
| `inactiveStaffResponse` | 2 | 鈴木二郎 | suzuki@example.com | STAFF | false | false | includeInactive=true の一覧・有効化テスト |

---

## 3. テストケース一覧

| TC 番号 | エンドポイント | HTTP | シナリオ | 前提条件 | 期待ステータス |
|---|---|---|---|---|---|
| TC-STAFF-01 | `/api/staffs` | GET | 認証済み ADMIN・includeInactive=false でアクティブ一覧取得 | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-STAFF-02 | `/api/staffs?includeInactive=true` | GET | 認証済み ADMIN・全スタッフ取得 | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-STAFF-03 | `/api/staffs` | GET | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-STAFF-04 | `/api/staffs` | GET | STAFF ロールによるアクセス | `@WithMockUser(roles="STAFF")` | 403 Forbidden |
| TC-STAFF-05 | `/api/staffs` | GET | スタッフが 0 件 | `@WithMockUser(roles="ADMIN")`、listStaffs=[] | 200 OK（空配列） |
| TC-STAFF-06 | `/api/staffs` | POST | 全フィールド指定で作成 | `@WithMockUser(roles="ADMIN")` | 201 Created |
| TC-STAFF-07 | `/api/staffs` | POST | name が blank | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-STAFF-08 | `/api/staffs` | POST | email が不正形式 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-STAFF-09 | `/api/staffs` | POST | password が 7 文字（min=8 未満） | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-STAFF-10 | `/api/staffs` | POST | password が null/省略 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-STAFF-11 | `/api/staffs` | POST | role が blank | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-STAFF-12 | `/api/staffs` | POST | 重複メールアドレス | `@WithMockUser(roles="ADMIN")` | 409 Conflict |
| TC-STAFF-13 | `/api/staffs` | POST | STAFF ロールによる作成試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |
| TC-STAFF-14 | `/api/staffs` | POST | 未認証での作成試行 | セッションなし | 401 Unauthorized |
| TC-STAFF-15 | `/api/staffs/{id}` | PUT | 全フィールド更新 | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-STAFF-16 | `/api/staffs/{id}` | PUT | password を省略（変更なし） | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-STAFF-17 | `/api/staffs/{id}` | PUT | password が 7 文字（min=8 未満） | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-STAFF-18 | `/api/staffs/{id}` | PUT | name が blank | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-STAFF-19 | `/api/staffs/{id}` | PUT | 存在しない ID | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-STAFF-20 | `/api/staffs/{id}` | PUT | 非アクティブスタッフの更新 | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-STAFF-21 | `/api/staffs/{id}` | PUT | STAFF ロールによる更新試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |
| TC-STAFF-22 | `/api/staffs/{id}/activate` | PATCH | 非アクティブスタッフを有効化 | `@WithMockUser(roles="ADMIN")` | 204 No Content |
| TC-STAFF-23 | `/api/staffs/{id}/activate` | PATCH | 存在しない ID で有効化 | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-STAFF-24 | `/api/staffs/{id}/activate` | PATCH | STAFF ロールによる有効化試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |
| TC-STAFF-25 | `/api/staffs/{id}` | DELETE | スタッフを無効化（論理削除） | `@WithMockUser(roles="ADMIN")` | 204 No Content |
| TC-STAFF-26 | `/api/staffs/{id}` | DELETE | 存在しない ID で無効化 | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-STAFF-27 | `/api/staffs/{id}` | DELETE | 自分自身の無効化 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-STAFF-28 | `/api/staffs/{id}` | DELETE | STAFF ロールによる無効化試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |

---

## 4. テストケース詳細

---

### TC-STAFF-01: 認証済み ADMIN によるアクティブスタッフ一覧取得（includeInactive=false）

**目的**

認証済み ADMIN ユーザーがデフォルトパラメーター（includeInactive=false）で一覧を取得すると、アクティブなスタッフのみが返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.listStaffs(false)` が `[activeStaffResponse]` を返すようにスタブ設定

**リクエスト**

```
GET /api/staffs
Authorization: （セッション済み、ADMIN ロール）
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
[
  {
    "id": 1,
    "name": "田中一郎",
    "email": "tanaka@example.com",
    "role": "ADMIN",
    "isActive": true
  }
]
```

**検証項目**

- ステータスコード: `200 OK`
- `$.length() == 1`
- `$[0].id == 1`
- `$[0].name == "田中一郎"`
- `$[0].email == "tanaka@example.com"`
- `$[0].role == "ADMIN"`
- `$[0].isActive == true`

**対応テストメソッド**: `should_return200_when_adminListsActiveStaffs`

---

### TC-STAFF-02: 認証済み ADMIN による全スタッフ一覧取得（includeInactive=true）

**目的**

ADMIN ユーザーが `includeInactive=true` で一覧を取得すると、アクティブ・非アクティブ両方のスタッフが返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.listStaffs(true)` が `[activeStaffResponse, inactiveStaffResponse]` を返すようにスタブ設定

**リクエスト**

```
GET /api/staffs?includeInactive=true
Authorization: （セッション済み、ADMIN ロール）
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
[
  { "id": 1, "isActive": true, ... },
  { "id": 2, "isActive": false, ... }
]
```

**検証項目**

- ステータスコード: `200 OK`
- `$.length() == 2`
- `$[0].isActive == true`
- `$[1].isActive == false`

**対応テストメソッド**: `should_return200_and_allStaffs_when_adminListsWithIncludeInactive`

---

### TC-STAFF-03: 未認証でのスタッフ一覧取得

**目的**

セッションなしでアクセスすると 401 が返ることを確認する。`/api/staffs/**` は `SecurityConfig` の URL ベース制限により認証必須となっている。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**リクエスト**

```
GET /api/staffs
```

**期待結果**

HTTP 401 Unauthorized + `application/json`:

```json
{ "message": "認証が必要です" }
```

**検証項目**

- ステータスコード: `401 Unauthorized`
- `$.message == "認証が必要です"`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`ListStaffs` 内）

---

### TC-STAFF-04: STAFF ロールによるスタッフ一覧取得試行

**目的**

STAFF ロールで `/api/staffs` にアクセスすると、URL ベースの ADMIN 制限により 403 が返ることを確認する。スタッフ管理機能は全エンドポイントが ADMIN 専用であり、STAFF ロールでのアクセスは一切禁止されている。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**リクエスト**

```
GET /api/staffs
Authorization: （セッション済み、STAFF ロール）
```

**期待結果**

HTTP 403 Forbidden + `application/json`:

```json
{ "message": "権限がありません" }
```

**検証項目**

- ステータスコード: `403 Forbidden`
- `$.message == "権限がありません"`

**対応テストメソッド**: `should_return403_when_staffRoleAccesses`

---

### TC-STAFF-05: スタッフが 0 件の場合の一覧取得

**目的**

アクティブなスタッフが存在しない場合、null ではなく空配列が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.listStaffs(false)` が空リストを返すようにスタブ設定

**リクエスト**

```
GET /api/staffs
Authorization: （セッション済み、ADMIN ロール）
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
[]
```

**検証項目**

- ステータスコード: `200 OK`
- `$` が配列であること
- `$.length() == 0`（`null` ではなく空配列）

**対応テストメソッド**: `should_return200_and_emptyList_when_noStaffExists`

---

### TC-STAFF-06: ADMIN によるスタッフ作成（全フィールド指定）

**目的**

ADMIN ユーザーが全フィールドを指定してスタッフを作成すると 201 Created が返り、作成されたスタッフ情報が含まれることを確認する。`forcePasswordChange=true` が新規作成時のデフォルト値であることも検証する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.createStaff(any(CreateStaffRequest.class))` が `activeStaffResponse` を返すようにスタブ設定

**リクエスト**

```
POST /api/staffs
Content-Type: application/json
Authorization: （セッション済み、ADMIN ロール）

{
  "name": "田中一郎",
  "email": "tanaka@example.com",
  "password": "password1",
  "role": "ADMIN"
}
```

**期待結果**

HTTP 201 Created + `application/json`:

```json
{
  "id": 1,
  "name": "田中一郎",
  "email": "tanaka@example.com",
  "role": "ADMIN",
  "isActive": true,
  "forcePasswordChange": true
}
```

**検証項目**

- ステータスコード: `201 Created`
- `$.id == 1`
- `$.name == "田中一郎"`
- `$.email == "tanaka@example.com"`
- `$.role == "ADMIN"`
- `$.isActive == true`（新規作成は常に有効）
- `$.forcePasswordChange == true`（新規作成時のデフォルト）

**対応テストメソッド**: `should_return201_when_adminCreatesValidStaff`

---

### TC-STAFF-07: name が blank（@NotBlank バリデーション）

**目的**

`name` フィールドに空文字を送信すると `@NotBlank` バリデーションに引っかかり 400 Bad Request が返り、`errors.name` にエラーメッセージが含まれることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/staffs
Content-Type: application/json

{
  "name": "",
  "email": "tanaka@example.com",
  "password": "password1",
  "role": "ADMIN"
}
```

**期待結果**

HTTP 400 Bad Request + `application/json`:

```json
{
  "message": "入力値が不正です",
  "errors": { "name": "<バリデーションメッセージ>" }
}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.message == "入力値が不正です"`
- `$.errors.name` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_nameIsBlank`（`CreateStaff` 内）

---

### TC-STAFF-08: email が不正形式（@Email バリデーション）

**目的**

`email` フィールドにメールアドレス形式でない文字列を送信すると `@Email` バリデーションに引っかかり 400 Bad Request が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/staffs
Content-Type: application/json

{
  "name": "田中一郎",
  "email": "not-an-email",
  "password": "password1",
  "role": "ADMIN"
}
```

**期待結果**

HTTP 400 Bad Request + `application/json`:

```json
{
  "message": "入力値が不正です",
  "errors": { "email": "<バリデーションメッセージ>" }
}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.errors.email` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_emailIsInvalid`

---

### TC-STAFF-09: password が 7 文字（@Size min=8 未満）

**目的**

`password` フィールドに 7 文字（最小文字数 8 の 1 文字未満）を送信すると `@Size(min=8)` バリデーションに引っかかり 400 Bad Request が返ることを確認する。境界値テスト（min-1）。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/staffs
Content-Type: application/json

{
  "name": "田中一郎",
  "email": "tanaka@example.com",
  "password": "1234567",
  "role": "ADMIN"
}
```

**期待結果**

HTTP 400 Bad Request + `application/json`:

```json
{
  "message": "入力値が不正です",
  "errors": { "password": "<バリデーションメッセージ>" }
}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.errors.password` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_passwordIsTooShort`（`CreateStaff` 内）

---

### TC-STAFF-10: password が null/省略（@NotBlank バリデーション）

**目的**

`password` フィールドを JSON から省略すると `@NotBlank` バリデーションに引っかかり 400 Bad Request が返ることを確認する。`CreateStaffRequest` では password は必須項目であり、`UpdateStaffRequest` とは異なる設計であることを検証する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/staffs
Content-Type: application/json

{
  "name": "田中一郎",
  "email": "tanaka@example.com",
  "role": "ADMIN"
}
```

**期待結果**

HTTP 400 Bad Request + `application/json`:

```json
{
  "message": "入力値が不正です",
  "errors": { "password": "<バリデーションメッセージ>" }
}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.errors.password` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_passwordIsNull`

---

### TC-STAFF-11: role が blank（@NotBlank バリデーション）

**目的**

`role` フィールドに空文字を送信すると `@NotBlank` バリデーションに引っかかり 400 Bad Request が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/staffs
Content-Type: application/json

{
  "name": "田中一郎",
  "email": "tanaka@example.com",
  "password": "password1",
  "role": ""
}
```

**期待結果**

HTTP 400 Bad Request + `application/json`:

```json
{
  "message": "入力値が不正です",
  "errors": { "role": "<バリデーションメッセージ>" }
}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.errors.role` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_roleIsBlank`

---

### TC-STAFF-12: 重複メールアドレスでのスタッフ作成

**目的**

既に登録済みのメールアドレスでスタッフを作成しようとすると 409 Conflict が返ることを確認する。`StaffService.createStaff` が `DuplicateResourceException` をスローし、`GlobalExceptionHandler` が 409 に変換する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.createStaff(any())` が `DuplicateResourceException("このメールアドレスは既に使用されています: tanaka@example.com")` をスローするようにスタブ設定

**リクエスト**

```
POST /api/staffs
Content-Type: application/json

{
  "name": "田中一郎",
  "email": "tanaka@example.com",
  "password": "password1",
  "role": "ADMIN"
}
```

**期待結果**

HTTP 409 Conflict + `application/json`:

```json
{ "message": "このメールアドレスは既に使用されています: tanaka@example.com" }
```

**検証項目**

- ステータスコード: `409 Conflict`
- `$.message == "このメールアドレスは既に使用されています: tanaka@example.com"`

**対応テストメソッド**: `should_return409_when_emailIsDuplicated`

---

### TC-STAFF-13: STAFF ロールによるスタッフ作成試行

**目的**

STAFF ロールで POST すると URL ベースの ADMIN 制限により 403 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**リクエスト**

```
POST /api/staffs
Content-Type: application/json

{
  "name": "田中一郎",
  "email": "tanaka@example.com",
  "password": "password1",
  "role": "ADMIN"
}
```

**検証項目**

- ステータスコード: `403 Forbidden`

**対応テストメソッド**: `should_return403_when_staffTriesToCreate`

---

### TC-STAFF-14: 未認証でのスタッフ作成試行

**目的**

セッションなしで POST すると 401 が返ることを確認する。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**リクエスト**

```
POST /api/staffs
Content-Type: application/json

{
  "name": "田中一郎",
  "email": "tanaka@example.com",
  "password": "password1",
  "role": "ADMIN"
}
```

**検証項目**

- ステータスコード: `401 Unauthorized`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`CreateStaff` 内）

---

### TC-STAFF-15: ADMIN によるスタッフ更新（全フィールド指定）

**目的**

ADMIN ユーザーが `name`・`role`・`password` を全て指定して PUT すると 200 OK が返り、更新後のスタッフ情報が含まれることを確認する。パスワードを変更した場合は `forcePasswordChange=true` がセットされる仕様も合わせて確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.updateStaff(eq(1L), any(UpdateStaffRequest.class))` が更新済みレスポンスを返すようにスタブ設定

**リクエスト**

```
PUT /api/staffs/1
Content-Type: application/json
Authorization: （セッション済み、ADMIN ロール）

{
  "name": "田中一郎（更新）",
  "role": "STAFF",
  "password": "newpass1"
}
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
{
  "id": 1,
  "name": "田中一郎（更新）",
  "role": "STAFF",
  ...
}
```

**検証項目**

- ステータスコード: `200 OK`
- `$.name == "田中一郎（更新）"`
- `$.role == "STAFF"`

**対応テストメソッド**: `should_return200_when_adminUpdatesStaff`

---

### TC-STAFF-16: password を省略した更新（変更なし）

**目的**

`UpdateStaffRequest` では `password` に `@NotBlank` が付与されていないため、省略（null）の場合はパスワード変更なしとして 200 OK が返ることを確認する。`CreateStaffRequest` と `UpdateStaffRequest` でバリデーション設計が異なる点を仕様として記録する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.updateStaff(eq(1L), any(UpdateStaffRequest.class))` が `activeStaffResponse` を返すようにスタブ設定

**リクエスト**

```
PUT /api/staffs/1
Content-Type: application/json
Authorization: （セッション済み、ADMIN ロール）

{
  "name": "田中一郎",
  "role": "ADMIN"
}
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
{ "id": 1, ... }
```

**検証項目**

- ステータスコード: `200 OK`
- `$.id == 1`
- `staffService.updateStaff(eq(1L), any(UpdateStaffRequest.class))` が 1 回呼ばれること（Mockito の `verify`）

**対応テストメソッド**: `should_return200_when_passwordIsOmitted`

---

### TC-STAFF-17: password が 7 文字（@Size min=8 未満）

**目的**

PUT での `password` フィールドに 7 文字を送信すると `@Size(min=8)` バリデーションに引っかかり 400 Bad Request が返ることを確認する。省略は許可されるが、値を送信した場合は最小文字数チェックが適用される。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
PUT /api/staffs/1
Content-Type: application/json

{
  "name": "田中一郎",
  "role": "ADMIN",
  "password": "1234567"
}
```

**期待結果**

HTTP 400 Bad Request + `application/json`:

```json
{
  "message": "入力値が不正です",
  "errors": { "password": "<バリデーションメッセージ>" }
}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.errors.password` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_passwordIsTooShort`（`UpdateStaff` 内）

---

### TC-STAFF-18: name が blank（@NotBlank バリデーション）

**目的**

PUT での `name` フィールドに空文字を送信すると `@NotBlank` バリデーションに引っかかり 400 Bad Request が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
PUT /api/staffs/1
Content-Type: application/json

{
  "name": "",
  "role": "ADMIN"
}
```

**期待結果**

HTTP 400 Bad Request + `application/json`:

```json
{
  "message": "入力値が不正です",
  "errors": { "name": "<バリデーションメッセージ>" }
}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.errors.name` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_nameIsBlank`（`UpdateStaff` 内）

---

### TC-STAFF-19: 存在しない ID でのスタッフ更新

**目的**

存在しない ID で PUT すると 404 が返ることを確認する。`StaffService.updateStaff` が `ResourceNotFoundException` をスローし、`GlobalExceptionHandler` が 404 に変換する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.updateStaff(eq(999L), any(UpdateStaffRequest.class))` が `ResourceNotFoundException("スタッフが見つかりません: 999")` をスローするようにスタブ設定

**リクエスト**

```
PUT /api/staffs/999
Content-Type: application/json

{
  "name": "田中一郎",
  "role": "ADMIN"
}
```

**期待結果**

HTTP 404 Not Found + `application/json`:

```json
{ "message": "スタッフが見つかりません: 999" }
```

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "スタッフが見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_staffNotFound`（`UpdateStaff` 内）

---

### TC-STAFF-20: 非アクティブスタッフの更新

**目的**

非アクティブ状態のスタッフ（`isActive=false`）を PUT で更新しようとすると 404 が返ることを確認する。サービス層が非アクティブスタッフを「見つからない」として扱う仕様を検証する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.updateStaff(eq(2L), any(UpdateStaffRequest.class))` が `ResourceNotFoundException("スタッフが見つかりません: 2")` をスローするようにスタブ設定

**リクエスト**

```
PUT /api/staffs/2
Content-Type: application/json

{
  "name": "鈴木二郎",
  "role": "STAFF"
}
```

**期待結果**

HTTP 404 Not Found + `application/json`:

```json
{ "message": "スタッフが見つかりません: 2" }
```

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "スタッフが見つかりません: 2"`

**対応テストメソッド**: `should_return404_when_staffIsInactive`

---

### TC-STAFF-21: STAFF ロールによるスタッフ更新試行

**目的**

STAFF ロールで PUT すると URL ベースの ADMIN 制限により 403 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**リクエスト**

```
PUT /api/staffs/1
Content-Type: application/json

{
  "name": "田中一郎",
  "role": "ADMIN"
}
```

**検証項目**

- ステータスコード: `403 Forbidden`

**対応テストメソッド**: `should_return403_when_staffRoleTriesToUpdate`

---

### TC-STAFF-22: ADMIN によるスタッフ有効化

**目的**

ADMIN が `PATCH /api/staffs/{id}/activate` を呼び出すと、非アクティブスタッフが有効化され 204 No Content が返ることを確認する。レスポンスボディなし。`staffService.activateStaff` が実際に呼ばれることを `verify` で確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.activateStaff(2L)` が何も返さない（`doNothing()`）ようにスタブ設定

**リクエスト**

```
PATCH /api/staffs/2/activate
Authorization: （セッション済み、ADMIN ロール）
```

**期待結果**

HTTP 204 No Content（レスポンスボディなし）

**検証項目**

- ステータスコード: `204 No Content`
- `staffService.activateStaff(2L)` が 1 回呼ばれること（Mockito の `verify`）

**対応テストメソッド**: `should_return204_when_adminActivatesStaff`

---

### TC-STAFF-23: 存在しない ID でのスタッフ有効化

**目的**

存在しない ID で有効化を試みると 404 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.activateStaff(999L)` が `ResourceNotFoundException("スタッフが見つかりません: 999")` をスローするようにスタブ設定

**リクエスト**

```
PATCH /api/staffs/999/activate
```

**期待結果**

HTTP 404 Not Found + `application/json`:

```json
{ "message": "スタッフが見つかりません: 999" }
```

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "スタッフが見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_staffNotFound`（`ActivateStaff` 内）

---

### TC-STAFF-24: STAFF ロールによるスタッフ有効化試行

**目的**

STAFF ロールで有効化を試みると URL ベースの ADMIN 制限により 403 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**リクエスト**

```
PATCH /api/staffs/2/activate
Authorization: （セッション済み、STAFF ロール）
```

**検証項目**

- ステータスコード: `403 Forbidden`

**対応テストメソッド**: `should_return403_when_staffRoleTriesToActivate`

---

### TC-STAFF-25: ADMIN によるスタッフ無効化（論理削除）

**目的**

ADMIN が `DELETE /api/staffs/{id}` を呼び出すと、スタッフが論理削除（`isActive=false`）され 204 No Content が返ることを確認する。レスポンスボディなし。`staffService.deactivateStaff` が実際に呼ばれることを `verify` で確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.deactivateStaff(2L)` が何も返さない（`doNothing()`）ようにスタブ設定

**リクエスト**

```
DELETE /api/staffs/2
Authorization: （セッション済み、ADMIN ロール）
```

**期待結果**

HTTP 204 No Content（レスポンスボディなし）

**検証項目**

- ステータスコード: `204 No Content`
- `staffService.deactivateStaff(2L)` が 1 回呼ばれること（Mockito の `verify`）

**対応テストメソッド**: `should_return204_when_adminDeactivatesStaff`

---

### TC-STAFF-26: 存在しない ID でのスタッフ無効化

**目的**

存在しない ID で DELETE すると 404 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.deactivateStaff(999L)` が `ResourceNotFoundException("スタッフが見つかりません: 999")` をスローするようにスタブ設定

**リクエスト**

```
DELETE /api/staffs/999
```

**期待結果**

HTTP 404 Not Found + `application/json`:

```json
{ "message": "スタッフが見つかりません: 999" }
```

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "スタッフが見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_staffNotFound`（`DeactivateStaff` 内）

---

### TC-STAFF-27: 自分自身の無効化

**目的**

ログイン中の ADMIN が自分自身のアカウントを無効化しようとすると 400 Bad Request が返ることを確認する。`StaffService.deactivateStaff` が `IllegalArgumentException` をスローし、`GlobalExceptionHandler` が 400 に変換する仕様を検証する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `staffService.deactivateStaff(1L)` が `IllegalArgumentException("自分自身を無効化することはできません")` をスローするようにスタブ設定

**リクエスト**

```
DELETE /api/staffs/1
Authorization: （セッション済み、ADMIN ロール）
```

**期待結果**

HTTP 400 Bad Request + `application/json`:

```json
{ "message": "自分自身を無効化することはできません" }
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.message == "自分自身を無効化することはできません"`

**対応テストメソッド**: `should_return400_when_adminTriesToDeactivateThemselves`

---

### TC-STAFF-28: STAFF ロールによるスタッフ無効化試行

**目的**

STAFF ロールで DELETE すると URL ベースの ADMIN 制限により 403 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**リクエスト**

```
DELETE /api/staffs/2
Authorization: （セッション済み、STAFF ロール）
```

**検証項目**

- ステータスコード: `403 Forbidden`

**対応テストメソッド**: `should_return403_when_staffRoleTriesToDeactivate`

---

## 5. 設計メモ

### 5.1 セキュリティ制御方針: URL ベース一括制限

`/api/staffs/**` は `SecurityConfig` の URL ベース設定で ADMIN ロールのみに一括制限されている。`UserController` の一部エンドポイントが採用する `@PreAuthorize("hasRole('ADMIN')")` によるメソッドレベル制限とは異なり、スタッフ管理 API は全エンドポイントを例外なく ADMIN 専用としているため、URL パターンでまとめて制御する方針がより適切と判断されている。

この結果、以下の動作が確定する。

- 未認証: 全エンドポイントで 401 Unauthorized
- STAFF ロール: 全エンドポイントで 403 Forbidden
- ADMIN ロール: 全エンドポイントにアクセス可能

`UserController` の `GET /api/users` のように「STAFF ロールでも読み取りを許可する」という例外的なケースが存在しないため、URL ベース一括制限が管理上も明快である。

### 5.2 バリデーション設計: CreateStaffRequest と UpdateStaffRequest の password 差異

`password` フィールドのバリデーションは POST と PUT で意図的に異なる設計を採用している。

| DTO | password のアノテーション | 省略時の挙動 |
|---|---|---|
| `CreateStaffRequest` | `@NotBlank` + `@Size(min=8, max=100)` | 400 Bad Request（必須） |
| `UpdateStaffRequest` | `@Size(min=8, max=100)` のみ | パスワード変更なしとして処理（省略可能） |

この設計により、更新時にパスワードを変更しない場合は `password` フィールドを JSON から省略できる。一方で、パスワードを送信した場合は必ず 8 文字以上のチェックが適用される（TC-STAFF-17）。

### 5.3 ビジネスルール: 非アクティブスタッフの更新拒否

`StaffService.updateStaff` はアクティブなスタッフのみを更新対象とする。`isActive=false` のスタッフ（論理削除済み）に対して PUT を送信しても、サービス層が `ResourceNotFoundException` をスローして 404 を返す（TC-STAFF-20）。

これは「論理削除されたリソースは存在しないものとして扱う」という一貫したビジネスルールに基づく設計である。有効化（PATCH /activate）で復旧してから更新する運用フローが想定されている。

### 5.4 ビジネスルール: 自己無効化の禁止

`StaffService.deactivateStaff` は、ログイン中のスタッフが自分自身のアカウントを無効化しようとした場合に `IllegalArgumentException` をスローする（TC-STAFF-27）。

`GlobalExceptionHandler` が `IllegalArgumentException` を 400 Bad Request にマッピングするため、フロントエンドは 400 でこの業務ルール違反を識別できる。自己ロックアウトによる管理者不在を防ぐための安全制御である。

### 5.5 ビジネスルール: 有効化・無効化の冪等性

`activateStaff` および `deactivateStaff` は、既に同じ状態のスタッフに対して呼ばれた場合でも `save` を実行しない。これにより `updated_at` の無駄な更新が防止される。テスト上は `doNothing()` スタブで表現しており、実際のステータス確認は DB 統合テストの領域となる。

### 5.6 ビジネスルール: メールアドレスの小文字正規化

`StaffService.createStaff` はメールアドレスを小文字に変換して保存する。これはバリデーション通過後にサービス層で実施されるため、テストコードでは `tanaka@example.com`（全て小文字）を使用している。大文字混じりのメールアドレスを入力した場合の動作確認は DB 統合テストで行う。

### 5.7 新規作成時の forcePasswordChange デフォルト

スタッフを新規作成すると `forcePasswordChange=true` が自動的に設定される（TC-STAFF-06）。これにより、初回ログイン時にパスワード変更を強制するフローが実現される。パスワード変更が完了すると `forcePasswordChange=false` に更新される設計となっている。

### 5.8 PATCH /activate と DELETE のレスポンス設計（204 No Content）

スタッフ管理 API の有効化・無効化エンドポイントはいずれも 204 No Content を返す（TC-STAFF-22・TC-STAFF-25）。これは `UserController` の `DELETE /api/users/{id}` が 200 OK + レスポンスボディを返す設計（論理削除後の最新状態を返す）とは異なる。スタッフ管理では操作完了の確認のみを目的とし、更新後の詳細情報は必要に応じて別途 GET で取得する設計方針を採用している。

### 5.9 Spring Boot 4.0 でのアノテーションパッケージ変更

Spring Boot 4.0（Spring Framework 7.0）では以下のパッケージが変更されている。3.x のコード例をそのままコピーするとコンパイルエラーになる。

| アノテーション | Spring Boot 3.x | Spring Boot 4.0 |
|---|---|---|
| `@AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@MockitoBean`（旧 `@MockBean`） | `org.springframework.boot.test.mock.mockito` | `org.springframework.test.context.bean.override.mockito` |
