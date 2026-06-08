# User API テスト仕様書

## 1. 概要

### テスト対象

`UserController` が処理する利用者管理エンドポイント全般。

| 対象ファイル | パス |
|---|---|
| テストクラス | `src/test/java/com/example/sendmail/controller/UserControllerTest.java` |
| プロダクションコード | `src/main/java/com/example/sendmail/controller/UserController.java` |
| サービス | `src/main/java/com/example/sendmail/service/UserService.java` |
| リクエスト DTO | `src/main/java/com/example/sendmail/dto/request/CreateUserRequest.java` |
| リクエスト DTO | `src/main/java/com/example/sendmail/dto/request/UpdateUserRequest.java` |
| リクエスト DTO | `src/main/java/com/example/sendmail/dto/request/AddOfficeToUserRequest.java` |
| レスポンス DTO | `src/main/java/com/example/sendmail/dto/response/UserResponse.java` |
| セキュリティ設定 | `src/main/java/com/example/sendmail/config/SecurityConfig.java` |
| 例外ハンドラー | `src/main/java/com/example/sendmail/exception/GlobalExceptionHandler.java` |

### 目的

- 利用者管理 API の全エンドポイントが正しい HTTP ステータスとレスポンスボディを返すことを保証する
- Spring Security の認証・認可フィルターが設計通りに動作することを確認する
- `UpdateUserRequest.notes` の `Optional<String>` による部分更新（未送信・null クリア・値あり）の挙動を仕様として記録する
- 異常系（バリデーションエラー・存在しないリソース・認証エラー・権限不足）の挙動を仕様として記録する

### 実行方法

```bash
# プロジェクトルートで実行
./mvnw test -Dtest=UserControllerTest

# 特定のネストクラスのみ実行
./mvnw test -Dtest="UserControllerTest\$CreateUser"

# 特定のテストケースのみ実行
./mvnw test -Dtest="UserControllerTest#should_return201_when_adminCreatesValidUser"
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
@DisplayName("UserController 統合テスト")
class UserControllerTest { ... }
```

`@SpringBootTest` により実際の `SecurityFilterChain` を起動し、`@PreAuthorize` や認証エントリーポイントを含む認証・認可フロー全体を通したテストを実現している。

### モック方針

| コンポーネント | 方針 | 理由 |
|---|---|---|
| `UserService` | `@MockitoBean` でモック | DB 接続なしにインメモリで完結させる |
| Spring Security 認証状態 | `@WithMockUser` で表現 | 実際のログインフローなしに認証済み状態を簡潔に設定できる |
| `DatabaseMigrator` | モックせず起動（エラー無視） | `setContinueOnError=true` で H2 上の MySQL 固有構文エラーを無視 |

### テストフィクスチャ

`@BeforeEach` で以下のレスポンスオブジェクトを初期化する。

| 変数 | 内容 | 用途 |
|---|---|---|
| `activeUserResponse` | id=1、name=山田太郎、isActive=true、offices なし | 正常系 GET/POST/PATCH/DELETE レスポンス |
| `inactiveUserResponse` | id=2、name=鈴木花子、isActive=false | includeInactive=true の一覧テスト |
| `userWithOfficesResponse` | id=1、offices=[東京事務所, 大阪事務所] | GET /api/users/{id}・POST offices レスポンス |
| `officeResponse1` | id=10、name=東京事務所 | 事業所一覧テスト用 |
| `officeResponse2` | id=20、name=大阪事務所 | 事業所一覧テスト用 |

---

## 3. テストケース一覧

| TC 番号 | エンドポイント | HTTP | シナリオ | 前提条件 | 期待ステータス |
|---|---|---|---|---|---|
| TC-USER-01 | `/api/users` | GET | 認証済み STAFF・includeInactive=false | `@WithMockUser(roles="STAFF")` | 200 OK |
| TC-USER-02 | `/api/users?includeInactive=true` | GET | 認証済み ADMIN・全利用者取得 | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-USER-03 | `/api/users?includeInactive=true` | GET | STAFF ロールによる全利用者取得試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |
| TC-USER-04 | `/api/users` | GET | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-USER-05 | `/api/users` | GET | 利用者が 0 件 | `@WithMockUser(roles="STAFF")`、listUsers=[] | 200 OK（空配列） |
| TC-USER-06 | `/api/users` | POST | 全フィールド指定で作成 | `@WithMockUser(roles="ADMIN")` | 201 Created |
| TC-USER-07 | `/api/users` | POST | 必須項目のみで作成 | `@WithMockUser(roles="ADMIN")` | 201 Created |
| TC-USER-08 | `/api/users` | POST | name が null | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-USER-09 | `/api/users` | POST | name が空文字 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-USER-10 | `/api/users` | POST | name が 101 文字 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-USER-11 | `/api/users` | POST | birthDate が未来日付 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-USER-12 | `/api/users` | POST | notes が 2001 文字 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-USER-13 | `/api/users` | POST | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-USER-14 | `/api/users` | POST | STAFF ロールによる作成試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |
| TC-USER-15 | `/api/users/{id}` | GET | 存在するユーザーを取得（offices 付き） | `@WithMockUser(roles="STAFF")` | 200 OK |
| TC-USER-16 | `/api/users/{id}` | GET | 存在しない ID で取得 | `@WithMockUser(roles="STAFF")` | 404 Not Found |
| TC-USER-17 | `/api/users/{id}` | GET | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-USER-18 | `/api/users/{id}` | GET | 事業所なしのユーザーを取得 | `@WithMockUser(roles="ADMIN")` | 200 OK（offices=空配列） |
| TC-USER-19 | `/api/users/{id}` | PATCH | 全フィールド更新 | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-USER-20 | `/api/users/{id}` | PATCH | notes=null 送信（クリア） | `@WithMockUser(roles="ADMIN")` | 200 OK（notes=null） |
| TC-USER-21 | `/api/users/{id}` | PATCH | notes フィールド未送信（変更なし） | `@WithMockUser(roles="ADMIN")` | 200 OK |
| TC-USER-22 | `/api/users/{id}` | PATCH | notes に値を送信（上書き） | `@WithMockUser(roles="ADMIN")` | 200 OK（notes=新値） |
| TC-USER-23 | `/api/users/{id}` | PATCH | 全フィールド未指定（空オブジェクト） | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-USER-24 | `/api/users/{id}` | PATCH | name が 101 文字 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-USER-25 | `/api/users/{id}` | PATCH | birthDate が未来日付 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-USER-26 | `/api/users/{id}` | PATCH | 存在しない ID で更新 | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-USER-27 | `/api/users/{id}` | PATCH | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-USER-28 | `/api/users/{id}` | PATCH | STAFF ロールによる更新試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |
| TC-USER-29 | `/api/users/{id}` | DELETE | 存在するユーザーを無効化 | `@WithMockUser(roles="ADMIN")` | 200 OK（isActive=false） |
| TC-USER-30 | `/api/users/{id}` | DELETE | 存在しない ID で無効化 | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-USER-31 | `/api/users/{id}` | DELETE | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-USER-32 | `/api/users/{id}` | DELETE | STAFF ロールによる無効化試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |
| TC-USER-33 | `/api/users/{id}/activate` | PATCH | 無効ユーザーを有効化 | `@WithMockUser(roles="ADMIN")` | 200 OK（isActive=true） |
| TC-USER-34 | `/api/users/{id}/activate` | PATCH | 存在しない ID で有効化 | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-USER-35 | `/api/users/{id}/activate` | PATCH | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-USER-36 | `/api/users/{id}/activate` | PATCH | STAFF ロールによる有効化試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |
| TC-USER-37 | `/api/users/{userId}/offices` | GET | 事業所あり利用者の事業所一覧取得 | `@WithMockUser(roles="STAFF")` | 200 OK |
| TC-USER-38 | `/api/users/{userId}/offices` | GET | 事業所なし利用者の事業所一覧取得 | `@WithMockUser(roles="STAFF")` | 200 OK（空配列） |
| TC-USER-39 | `/api/users/{userId}/offices` | GET | 存在しないユーザー ID | `@WithMockUser(roles="STAFF")` | 404 Not Found |
| TC-USER-40 | `/api/users/{userId}/offices` | GET | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-USER-41 | `/api/users/{userId}/offices` | POST | 有効な officeId で紐付け | `@WithMockUser(roles="ADMIN")` | 201 Created |
| TC-USER-42 | `/api/users/{userId}/offices` | POST | officeId が null | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-USER-43 | `/api/users/{userId}/offices` | POST | officeId が 0 | `@WithMockUser(roles="ADMIN")` | 400 Bad Request |
| TC-USER-44 | `/api/users/{userId}/offices` | POST | 存在しないユーザー ID | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-USER-45 | `/api/users/{userId}/offices` | POST | 存在しない事業所 ID | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-USER-46 | `/api/users/{userId}/offices` | POST | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-USER-47 | `/api/users/{userId}/offices` | POST | STAFF ロールによる紐付け試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |
| TC-USER-48 | `/api/users/{userId}/offices/{officeId}` | DELETE | 存在する紐付けを解除 | `@WithMockUser(roles="ADMIN")` | 204 No Content |
| TC-USER-49 | `/api/users/{userId}/offices/{officeId}` | DELETE | 存在しないユーザー ID | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-USER-50 | `/api/users/{userId}/offices/{officeId}` | DELETE | 存在しない紐付け | `@WithMockUser(roles="ADMIN")` | 404 Not Found |
| TC-USER-51 | `/api/users/{userId}/offices/{officeId}` | DELETE | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-USER-52 | `/api/users/{userId}/offices/{officeId}` | DELETE | STAFF ロールによる解除試行 | `@WithMockUser(roles="STAFF")` | 403 Forbidden |

---

## 4. テストケース詳細

---

### TC-USER-01: 認証済み STAFF による利用者一覧取得（includeInactive=false）

**目的**

認証済み STAFF ユーザーがデフォルトパラメーター（includeInactive=false）で一覧を取得すると、アクティブな利用者のみが返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み
- `userService.listUsers(false)` が `[activeUserResponse]` を返すようにスタブ設定

**リクエスト**

```
GET /api/users
Authorization: （セッション済み、STAFF ロール）
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
[
  { "id": 1, "name": "山田太郎", "isActive": true, ... }
]
```

**検証項目**

- `$.length() == 1`
- `$[0].id == 1`
- `$[0].name == "山田太郎"`
- `$[0].isActive == true`

**対応テストメソッド**: `should_return200_when_authenticatedStaffListsActiveUsers`

---

### TC-USER-02: 認証済み ADMIN による全利用者一覧取得（includeInactive=true）

**目的**

ADMIN ユーザーが `includeInactive=true` で一覧を取得すると、アクティブ・非アクティブ両方の利用者が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.listUsers(true)` が `[activeUserResponse, inactiveUserResponse]` を返すようにスタブ設定

**リクエスト**

```
GET /api/users?includeInactive=true
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

- `$.length() == 2`
- `$[0].isActive == true`
- `$[1].isActive == false`

**対応テストメソッド**: `should_return200_and_allUsers_when_adminListsWithIncludeInactive`

---

### TC-USER-03: STAFF ロールによる includeInactive=true アクセス拒否

**目的**

STAFF ロールのユーザーが `includeInactive=true` でアクセスすると 403 が返ることを確認する。コントローラーが `Authentication` のロールを手動検査して `AccessDeniedException` をスローする設計を検証する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**リクエスト**

```
GET /api/users?includeInactive=true
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
- コントローラー内の手動ロールチェックにより `GlobalExceptionHandler.handleAccessDenied` が応答すること

**対応テストメソッド**: `should_return403_when_staffRequestsIncludeInactive`

---

### TC-USER-04: 未認証での利用者一覧取得

**目的**

セッションなしでアクセスすると 401 が返ることを確認する。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**リクエスト**

```
GET /api/users
```

**期待結果**

HTTP 401 Unauthorized + `application/json`:

```json
{ "message": "認証が必要です" }
```

**検証項目**

- ステータスコード: `401 Unauthorized`
- `$.message == "認証が必要です"`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`ListUsers` 内）

---

### TC-USER-05: 利用者が 0 件の場合の一覧取得

**目的**

アクティブな利用者が存在しない場合、null ではなく空配列が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み
- `userService.listUsers(false)` が空リストを返すようにスタブ設定

**期待結果**

HTTP 200 OK + `application/json`:

```json
[]
```

**検証項目**

- ステータスコード: `200 OK`
- `$` が配列であること
- `$.length() == 0`（`null` ではなく空配列）

**対応テストメソッド**: `should_return200_and_emptyList_when_noUsersExist`

---

### TC-USER-06: ADMIN による利用者作成（全フィールド指定）

**目的**

ADMIN ユーザーが全フィールドを指定して利用者を作成すると 201 Created が返り、作成された利用者情報が含まれることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.createUser(any())` が `activeUserResponse` を返すようにスタブ設定

**リクエスト**

```
POST /api/users
Content-Type: application/json
Authorization: （セッション済み、ADMIN ロール）

{
  "name": "山田太郎",
  "nameKana": "ヤマダタロウ",
  "birthDate": "1980-04-01",
  "notes": "備考テスト"
}
```

**期待結果**

HTTP 201 Created + `application/json`:

```json
{
  "id": 1,
  "name": "山田太郎",
  "nameKana": "ヤマダタロウ",
  "isActive": true,
  ...
}
```

**検証項目**

- ステータスコード: `201 Created`
- `$.id == 1`
- `$.name == "山田太郎"`
- `$.nameKana == "ヤマダタロウ"`
- `$.isActive == true`（新規作成は常に有効）

**対応テストメソッド**: `should_return201_when_adminCreatesValidUser`

---

### TC-USER-07: 必須項目のみで利用者作成

**目的**

`name` のみ（必須項目）を指定して作成すると 201 Created が返ることを確認する。`nameKana`・`birthDate`・`notes` は省略可能フィールドである。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.createUser(any())` が `id=3`・`name=田中次郎` のレスポンスを返すようにスタブ設定

**リクエスト**

```
POST /api/users
Content-Type: application/json

{"name": "田中次郎"}
```

**期待結果**

HTTP 201 Created:

- `$.id == 3`
- `$.name == "田中次郎"`

**検証項目**

- ステータスコード: `201 Created`
- `$.name == "田中次郎"`

**対応テストメソッド**: `should_return201_when_onlyRequiredFieldsProvided`

---

### TC-USER-08: name が null（@NotBlank バリデーション）

**目的**

`name` フィールドを省略すると 400 Bad Request が返り、`errors.name` にバリデーションエラーメッセージが含まれることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/users
Content-Type: application/json

{"nameKana": "テスト"}
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
- `$.errors.name` フィールドが存在すること（`@NotBlank` バリデーション）

**対応テストメソッド**: `should_return400_when_nameIsNull`

---

### TC-USER-09: name が空文字（@NotBlank バリデーション）

**目的**

`name` に空文字を送信すると 400 Bad Request が返ることを確認する。`@NotBlank` は空文字・空白のみの文字列を拒否する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/users
Content-Type: application/json

{"name": ""}
```

**期待結果**

HTTP 400 Bad Request:

- `$.message == "入力値が不正です"`
- `$.errors.name` フィールドが存在すること

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.errors.name` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_nameIsBlank`

---

### TC-USER-10: name が 101 文字（@Size max=100 超え）

**目的**

`name` に 101 文字の文字列を送信すると `@Size(max=100)` バリデーションに引っかかり 400 Bad Request が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/users
Content-Type: application/json

{"name": "あ" × 101文字}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.message == "入力値が不正です"`
- `$.errors.name` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_nameExceedsMaxLength`（`CreateUser` 内）

---

### TC-USER-11: birthDate が未来日付（@Past バリデーション）

**目的**

`birthDate` に未来の日付を送信すると `@Past` バリデーションに引っかかり 400 Bad Request が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/users
Content-Type: application/json

{
  "name": "山田太郎",
  "birthDate": "2099-01-01"
}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.message == "入力値が不正です"`
- `$.errors.birthDate` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_birthDateIsFuture`（`CreateUser` 内）

---

### TC-USER-12: notes が 2001 文字（@Size max=2000 超え）

**目的**

`notes` に 2001 文字の文字列を送信すると `@Size(max=2000)` バリデーションに引っかかり 400 Bad Request が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/users
Content-Type: application/json

{"name": "山田太郎", "notes": "a" × 2001文字}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.message == "入力値が不正です"`
- `$.errors.notes` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_notesExceedsMaxLength`（`CreateUser` 内）

---

### TC-USER-13: 未認証での利用者作成

**目的**

セッションなしで POST すると 401 が返ることを確認する。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**リクエスト**

```
POST /api/users
Content-Type: application/json

{"name": "山田太郎"}
```

**検証項目**

- ステータスコード: `401 Unauthorized`
- `$.message == "認証が必要です"`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`CreateUser` 内）

---

### TC-USER-14: STAFF ロールによる利用者作成試行

**目的**

STAFF ロールで POST すると `@PreAuthorize("hasRole('ADMIN')")` により 403 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**リクエスト**

```
POST /api/users
Content-Type: application/json

{"name": "山田太郎"}
```

**検証項目**

- ステータスコード: `403 Forbidden`
- `$.message == "権限がありません"`

**対応テストメソッド**: `should_return403_when_staffTriesToCreate`

---

### TC-USER-15: 利用者取得（事業所付き）

**目的**

認証済みユーザーが存在する利用者を取得すると、事業所リストを含む `UserResponse` が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み
- `userService.getUser(1L)` が `userWithOfficesResponse`（2 事業所付き）を返すようにスタブ設定

**リクエスト**

```
GET /api/users/1
Authorization: （セッション済み）
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
{
  "id": 1,
  "name": "山田太郎",
  "nameKana": "ヤマダタロウ",
  "isActive": true,
  "offices": [
    { "id": 10, "name": "東京事務所" },
    { "id": 20, "name": "大阪事務所" }
  ]
}
```

**検証項目**

- ステータスコード: `200 OK`
- `$.id == 1`
- `$.name == "山田太郎"`
- `$.nameKana == "ヤマダタロウ"`
- `$.isActive == true`
- `$.offices` が配列であること
- `$.offices.length() == 2`
- `$.offices[0].id == 10`
- `$.offices[0].name == "東京事務所"`

**対応テストメソッド**: `should_return200_and_userWithOffices_when_userExists`

---

### TC-USER-16: 存在しない ID での利用者取得

**目的**

存在しない ID で取得すると 404 が返ることを確認する。`UserService.getUser` が `ResourceNotFoundException` をスローし、`GlobalExceptionHandler` が 404 に変換する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み
- `userService.getUser(999L)` が `ResourceNotFoundException("利用者が見つかりません: 999")` をスローするようにスタブ設定

**リクエスト**

```
GET /api/users/999
```

**期待結果**

HTTP 404 Not Found + `application/json`:

```json
{ "message": "利用者が見つかりません: 999" }
```

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "利用者が見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_userNotFound`（`GetUser` 内）

---

### TC-USER-17: 未認証での利用者取得

**目的**

セッションなしで GET すると 401 が返ることを確認する。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**検証項目**

- ステータスコード: `401 Unauthorized`
- `$.message == "認証が必要です"`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`GetUser` 内）

---

### TC-USER-18: 事業所なし利用者の取得

**目的**

事業所が紐付いていない利用者を取得すると `offices` が空配列で返ることを確認する。`@JsonInclude(NON_NULL)` の設定上 `null` の場合はフィールドがシリアライズされないが、空リストを渡した場合は `"offices": []` として返る仕様を検証する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.getUser(1L)` が `offices=空リスト` の `UserResponse` を返すようにスタブ設定

**検証項目**

- ステータスコード: `200 OK`
- `$.offices` が配列であること
- `$.offices.length() == 0`

**対応テストメソッド**: `should_return200_with_emptyOffices_when_userHasNoOffices`

---

### TC-USER-19: 全フィールド更新

**目的**

ADMIN ユーザーが `name`・`nameKana`・`birthDate`・`notes` を全て指定して PATCH すると 200 OK が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.updateUser(eq(1L), any())` が更新済みレスポンスを返すようにスタブ設定

**リクエスト**

```
PATCH /api/users/1
Content-Type: application/json
Authorization: （セッション済み、ADMIN ロール）

{
  "name": "山田太郎（更新）",
  "nameKana": "ヤマダタロウ",
  "birthDate": "1980-04-01",
  "notes": "更新済み備考"
}
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
{
  "id": 1,
  "name": "山田太郎（更新）",
  "notes": "更新済み備考",
  ...
}
```

**検証項目**

- ステータスコード: `200 OK`
- `$.id == 1`
- `$.name == "山田太郎（更新）"`
- `$.notes == "更新済み備考"`

**対応テストメソッド**: `should_return200_when_adminUpdatesAllFields`

---

### TC-USER-20: notes を null 送信（クリア操作）

**目的**

`notes` フィールドに JSON の `null` を明示的に送信すると notes がクリアされることを確認する。`UpdateUserRequest.notes` の `@JsonSetter(nulls = Nulls.AS_EMPTY)` による三値パターンの「クリア操作」を検証する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.updateUser(eq(1L), any())` が `notes=null` のレスポンスを返すようにスタブ設定

**リクエスト**

```
PATCH /api/users/1
Content-Type: application/json

{
  "name": "山田太郎",
  "notes": null
}
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
{ "id": 1, "name": "山田太郎", "notes": null, ... }
```

**検証項目**

- ステータスコード: `200 OK`
- `$.notes` が空（null）であること

**対応テストメソッド**: `should_return200_with_nullNotes_when_notesSentAsNull`

---

### TC-USER-21: notes フィールドを未送信（変更なし）

**目的**

`notes` フィールドを JSON に含めない（フィールド自体を省略）場合、notes が変更されないことを確認する。`UpdateUserRequest.notes` のデフォルト値は Java の `null` であり、サービス層は notes の更新をスキップする。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.updateUser(eq(1L), any())` が `activeUserResponse` を返すようにスタブ設定

**リクエスト**

```
PATCH /api/users/1
Content-Type: application/json

{"name": "山田太郎"}
```

**検証項目**

- ステータスコード: `200 OK`
- `$.id == 1`
- `userService.updateUser(eq(1L), any())` が呼ばれること（Mockito の `verify`）

**対応テストメソッド**: `should_return200_when_notesFieldIsAbsent`

---

### TC-USER-22: notes に値を送信（上書き）

**目的**

`notes` に文字列を送信すると既存の notes が上書きされることを確認する。`Optional.of("新しい備考")` としてサービス層に渡り、エンティティが更新される。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.updateUser(eq(1L), any())` が `notes="新しい備考"` のレスポンスを返すようにスタブ設定

**リクエスト**

```
PATCH /api/users/1
Content-Type: application/json

{
  "name": "山田太郎",
  "notes": "新しい備考"
}
```

**期待結果**

HTTP 200 OK:

```json
{ "notes": "新しい備考", ... }
```

**検証項目**

- ステータスコード: `200 OK`
- `$.notes == "新しい備考"`

**対応テストメソッド**: `should_return200_with_updatedNotes_when_notesValueProvided`

---

### TC-USER-23: 全フィールド未指定（空オブジェクト）

**目的**

`{}` を送信すると、全フィールドが未指定となりサービスが `IllegalStateException` をスローして 400 が返ることを確認する。`GlobalExceptionHandler` が `IllegalStateException` を 400 Bad Request に変換する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.updateUser(eq(1L), any())` が `IllegalStateException("更新するフィールドが指定されていません")` をスローするようにスタブ設定

**リクエスト**

```
PATCH /api/users/1
Content-Type: application/json

{}
```

**期待結果**

HTTP 400 Bad Request + `application/json`:

```json
{ "message": "更新するフィールドが指定されていません" }
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.message == "更新するフィールドが指定されていません"`

**対応テストメソッド**: `should_return400_when_noFieldsSpecified`

---

### TC-USER-24: name が 101 文字（@Size max=100 超え）

**目的**

PATCH でも `name` に 101 文字の文字列を送信すると `@Size(max=100)` バリデーションに引っかかり 400 Bad Request が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
PATCH /api/users/1
Content-Type: application/json

{"name": "い" × 101文字}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.message == "入力値が不正です"`
- `$.errors.name` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_nameExceedsMaxLength`（`UpdateUser` 内）

---

### TC-USER-25: birthDate が未来日付（@Past バリデーション）

**目的**

PATCH でも `birthDate` に未来の日付を送信すると `@Past` バリデーションに引っかかり 400 Bad Request が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
PATCH /api/users/1
Content-Type: application/json

{"birthDate": "2099-12-31"}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.message == "入力値が不正です"`
- `$.errors.birthDate` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_birthDateIsFuture`（`UpdateUser` 内）

---

### TC-USER-26: 存在しない ID での更新

**目的**

存在しない ID で PATCH すると 404 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.updateUser(eq(999L), any())` が `ResourceNotFoundException("利用者が見つかりません: 999")` をスローするようにスタブ設定

**リクエスト**

```
PATCH /api/users/999
Content-Type: application/json

{"name": "山田太郎"}
```

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "利用者が見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_userNotFound`（`UpdateUser` 内）

---

### TC-USER-27: 未認証での更新

**目的**

セッションなしで PATCH すると 401 が返ることを確認する。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**検証項目**

- ステータスコード: `401 Unauthorized`
- `$.message == "認証が必要です"`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`UpdateUser` 内）

---

### TC-USER-28: STAFF ロールによる更新試行

**目的**

STAFF ロールで PATCH すると `@PreAuthorize("hasRole('ADMIN')")` により 403 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**検証項目**

- ステータスコード: `403 Forbidden`
- `$.message == "権限がありません"`

**対応テストメソッド**: `should_return403_when_staffTriesToUpdate`

---

### TC-USER-29: 利用者無効化（論理削除）

**目的**

ADMIN が `DELETE /api/users/{id}` を呼び出すと `isActive=false` になった利用者情報が返ることを確認する。HTTP DELETE であるが 200 OK + レスポンスボディを返す設計（論理削除）を検証する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.deactivateUser(1L)` が `isActive=false` のレスポンスを返すようにスタブ設定

**リクエスト**

```
DELETE /api/users/1
Authorization: （セッション済み、ADMIN ロール）
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
{ "id": 1, "isActive": false, ... }
```

**検証項目**

- ステータスコード: `200 OK`
- `$.id == 1`
- `$.isActive == false`

**対応テストメソッド**: `should_return200_and_deactivatedUser_when_adminDeactivates`

---

### TC-USER-30: 存在しない ID での無効化

**目的**

存在しない ID で DELETE すると 404 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.deactivateUser(999L)` が `ResourceNotFoundException` をスローするようにスタブ設定

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "利用者が見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_userNotFound`（`DeactivateUser` 内）

---

### TC-USER-31: 未認証での無効化

**目的**

セッションなしで DELETE すると 401 が返ることを確認する。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**検証項目**

- ステータスコード: `401 Unauthorized`
- `$.message == "認証が必要です"`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`DeactivateUser` 内）

---

### TC-USER-32: STAFF ロールによる無効化試行

**目的**

STAFF ロールで DELETE すると `@PreAuthorize("hasRole('ADMIN')")` により 403 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**検証項目**

- ステータスコード: `403 Forbidden`
- `$.message == "権限がありません"`

**対応テストメソッド**: `should_return403_when_staffTriesToDeactivate`

---

### TC-USER-33: 利用者有効化

**目的**

ADMIN が `PATCH /api/users/{id}/activate` を呼び出すと `isActive=true` になった利用者情報が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.activateUser(2L)` が `activeUserResponse`（isActive=true）を返すようにスタブ設定

**リクエスト**

```
PATCH /api/users/2/activate
Authorization: （セッション済み、ADMIN ロール）
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
{ "isActive": true, ... }
```

**検証項目**

- ステータスコード: `200 OK`
- `$.isActive == true`

**対応テストメソッド**: `should_return200_and_activatedUser_when_adminActivates`

---

### TC-USER-34: 存在しない ID での有効化

**目的**

存在しない ID で有効化すると 404 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.activateUser(999L)` が `ResourceNotFoundException` をスローするようにスタブ設定

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "利用者が見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_userNotFound`（`ActivateUser` 内）

---

### TC-USER-35: 未認証での有効化

**目的**

セッションなしで有効化すると 401 が返ることを確認する。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**検証項目**

- ステータスコード: `401 Unauthorized`
- `$.message == "認証が必要です"`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`ActivateUser` 内）

---

### TC-USER-36: STAFF ロールによる有効化試行

**目的**

STAFF ロールで有効化すると `@PreAuthorize("hasRole('ADMIN')")` により 403 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**検証項目**

- ステータスコード: `403 Forbidden`
- `$.message == "権限がありません"`

**対応テストメソッド**: `should_return403_when_staffTriesToActivate`

---

### TC-USER-37: 事業所あり利用者の事業所一覧取得

**目的**

認証済みユーザーが事業所紐付きの利用者の事業所一覧を取得すると、事業所リストが返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み
- `userService.getUserOffices(1L)` が `[officeResponse1, officeResponse2]` を返すようにスタブ設定

**リクエスト**

```
GET /api/users/1/offices
Authorization: （セッション済み）
```

**期待結果**

HTTP 200 OK + `application/json`:

```json
[
  { "id": 10, "name": "東京事務所" },
  { "id": 20, "name": "大阪事務所" }
]
```

**検証項目**

- ステータスコード: `200 OK`
- `$.length() == 2`
- `$[0].id == 10`
- `$[0].name == "東京事務所"`
- `$[1].id == 20`
- `$[1].name == "大阪事務所"`

**対応テストメソッド**: `should_return200_and_officeList_when_userHasOffices`

---

### TC-USER-38: 事業所なし利用者の事業所一覧取得

**目的**

事業所が紐付いていない利用者の事業所一覧を取得すると空配列が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み
- `userService.getUserOffices(1L)` が空リストを返すようにスタブ設定

**検証項目**

- ステータスコード: `200 OK`
- `$` が配列であること
- `$.length() == 0`

**対応テストメソッド**: `should_return200_and_emptyList_when_userHasNoOffices`（`GetUserOffices` 内）

---

### TC-USER-39: 存在しないユーザー ID での事業所一覧取得

**目的**

存在しないユーザー ID で事業所一覧を取得すると 404 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み
- `userService.getUserOffices(999L)` が `ResourceNotFoundException` をスローするようにスタブ設定

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "利用者が見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_userNotFound`（`GetUserOffices` 内）

---

### TC-USER-40: 未認証での事業所一覧取得

**目的**

セッションなしで事業所一覧を取得すると 401 が返ることを確認する。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**検証項目**

- ステータスコード: `401 Unauthorized`
- `$.message == "認証が必要です"`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`GetUserOffices` 内）

---

### TC-USER-41: 事業所紐付け

**目的**

ADMIN が `POST /api/users/{userId}/offices` に有効な `officeId` を送信すると、紐付け後の利用者情報（事業所リスト付き）が 201 Created で返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.addOfficeToUser(eq(1L), eq(10L))` が `userWithOfficesResponse` を返すようにスタブ設定

**リクエスト**

```
POST /api/users/1/offices
Content-Type: application/json
Authorization: （セッション済み、ADMIN ロール）

{"officeId": 10}
```

**期待結果**

HTTP 201 Created + `application/json`:

```json
{
  "id": 1,
  "offices": [...]
}
```

**検証項目**

- ステータスコード: `201 Created`
- `$.id == 1`
- `$.offices` が配列であること
- `$.offices.length() == 2`

**対応テストメソッド**: `should_return201_and_userWithOffice_when_adminAddsOffice`

---

### TC-USER-42: officeId が null（@NotNull バリデーション）

**目的**

`officeId` に `null` を送信すると `@NotNull` バリデーションに引っかかり 400 Bad Request が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/users/1/offices
Content-Type: application/json

{"officeId": null}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.message == "入力値が不正です"`
- `$.errors.officeId` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_officeIdIsNull`

---

### TC-USER-43: officeId が 0（@Positive バリデーション）

**目的**

`officeId` に `0` を送信すると `@Positive` バリデーションに引っかかり 400 Bad Request が返ることを確認する。正の整数のみが有効な事業所 ID として受け付けられる。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み

**リクエスト**

```
POST /api/users/1/offices
Content-Type: application/json

{"officeId": 0}
```

**検証項目**

- ステータスコード: `400 Bad Request`
- `$.message == "入力値が不正です"`
- `$.errors.officeId` フィールドが存在すること

**対応テストメソッド**: `should_return400_when_officeIdIsZero`

---

### TC-USER-44: 存在しないユーザー ID で事業所紐付け

**目的**

存在しないユーザー ID で事業所紐付けを試みると 404 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.addOfficeToUser(eq(999L), anyLong())` が `ResourceNotFoundException` をスローするようにスタブ設定

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "利用者が見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_userNotFound`（`AddOfficeToUser` 内）

---

### TC-USER-45: 存在しない事業所 ID で事業所紐付け

**目的**

存在しない事業所 ID で紐付けを試みると 404 が返ることを確認する。`UserService.addOfficeToUser` が `OfficeRepository.findById` の結果が空の場合に `ResourceNotFoundException` をスローする。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.addOfficeToUser(eq(1L), eq(999L))` が `ResourceNotFoundException("事業所が見つかりません: 999")` をスローするようにスタブ設定

**リクエスト**

```
POST /api/users/1/offices
Content-Type: application/json

{"officeId": 999}
```

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "事業所が見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_officeNotFound`

---

### TC-USER-46: 未認証での事業所紐付け

**目的**

セッションなしで事業所紐付けを試みると 401 が返ることを確認する。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**検証項目**

- ステータスコード: `401 Unauthorized`
- `$.message == "認証が必要です"`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`AddOfficeToUser` 内）

---

### TC-USER-47: STAFF ロールによる事業所紐付け試行

**目的**

STAFF ロールで事業所紐付けを試みると `@PreAuthorize("hasRole('ADMIN')")` により 403 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**検証項目**

- ステータスコード: `403 Forbidden`
- `$.message == "権限がありません"`

**対応テストメソッド**: `should_return403_when_staffTriesToAddOffice`

---

### TC-USER-48: 事業所紐付け解除

**目的**

ADMIN が `DELETE /api/users/{userId}/offices/{officeId}` を呼び出すと 204 No Content が返ることを確認する。レスポンスボディなし。`userService.removeOfficeFromUser` が実際に呼ばれることを `verify` で確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.removeOfficeFromUser(1L, 10L)` が何も返さない（`doNothing()`）ようにスタブ設定

**リクエスト**

```
DELETE /api/users/1/offices/10
Authorization: （セッション済み、ADMIN ロール）
```

**期待結果**

HTTP 204 No Content（レスポンスボディなし）

**検証項目**

- ステータスコード: `204 No Content`
- `userService.removeOfficeFromUser(1L, 10L)` が 1 回呼ばれること（Mockito の `verify`）

**対応テストメソッド**: `should_return204_when_adminRemovesOffice`

---

### TC-USER-49: 存在しないユーザー ID で紐付け解除

**目的**

存在しないユーザー ID で紐付け解除を試みると 404 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.removeOfficeFromUser(999L, 10L)` が `ResourceNotFoundException` をスローするようにスタブ設定

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "利用者が見つかりません: 999"`

**対応テストメソッド**: `should_return404_when_userNotFound`（`RemoveOfficeFromUser` 内）

---

### TC-USER-50: 存在しない紐付けの解除

**目的**

指定した `userId` と `officeId` の組み合わせが紐付けテーブルに存在しない場合、404 が返ることを確認する。`UserOfficeRepository.findByUserIdAndOfficeId` が空を返すことで `ResourceNotFoundException` がスローされる。

**前提条件**

- `@WithMockUser(roles="ADMIN")` により認証済み
- `userService.removeOfficeFromUser(1L, 999L)` が `ResourceNotFoundException("紐付けが見つかりません")` をスローするようにスタブ設定

**検証項目**

- ステータスコード: `404 Not Found`
- `$.message == "紐付けが見つかりません"`

**対応テストメソッド**: `should_return404_when_associationNotFound`

---

### TC-USER-51: 未認証での紐付け解除

**目的**

セッションなしで紐付け解除を試みると 401 が返ることを確認する。

**前提条件**

- 認証なし（`@WithMockUser` なし）

**検証項目**

- ステータスコード: `401 Unauthorized`
- `$.message == "認証が必要です"`

**対応テストメソッド**: `should_return401_when_notAuthenticated`（`RemoveOfficeFromUser` 内）

---

### TC-USER-52: STAFF ロールによる紐付け解除試行

**目的**

STAFF ロールで紐付け解除を試みると `@PreAuthorize("hasRole('ADMIN')")` により 403 が返ることを確認する。

**前提条件**

- `@WithMockUser(roles="STAFF")` により認証済み

**検証項目**

- ステータスコード: `403 Forbidden`
- `$.message == "権限がありません"`

**対応テストメソッド**: `should_return403_when_staffTriesToRemoveOffice`

---

## 5. テスト設計上の注意点

### 5.1 UpdateUserRequest.notes の Optional<String> 三値パターン

`UpdateUserRequest.notes` は `Optional<String>` 型で、`@JsonSetter(nulls = Nulls.AS_EMPTY)` アノテーションが付与されている。これにより JSON の挙動が以下のように三値に分岐する。

| JSON 送信内容 | Java 側の値 | サービスの動作 |
|---|---|---|
| `notes` フィールドなし | `null`（Java の null） | notes を変更しない（TC-USER-21） |
| `"notes": null` | `Optional.empty()` | notes を null にクリア（TC-USER-20） |
| `"notes": "値"` | `Optional.of("値")` | notes を "値" に更新（TC-USER-22） |

この三値パターンは `UpdateUserRequest` のように部分更新（PATCH）で「フィールドを省略した場合は変更しない」「`null` を明示した場合はクリアする」という挙動を実現するための設計である。

### 5.2 DELETE /api/users/{id} は 204 No Content ではなく 200 OK + レスポンスボディ

一般的な REST では DELETE は 204 No Content を返すことが多いが、このAPIでは `UserService.deactivateUser` が無効化後の `UserResponse` を返し、コントローラーに明示的な `@ResponseStatus` が付与されていないため 200 OK + レスポンスボディが返る設計となっている。論理削除（`isActive=false`）であり物理削除ではないため、更新後の状態をそのままレスポンスに含めることで呼び出し元が追加の GET なしに最新状態を得られる。

### 5.3 DELETE /api/users/{userId}/offices/{officeId} は 204 No Content

事業所紐付け解除（`removeOfficeFromUser`）は戻り値が `void` であり、コントローラーに `@ResponseStatus(HttpStatus.NO_CONTENT)` が付与されているため 204 No Content を返す（レスポンスボディなし）。TC-USER-29 の利用者無効化（200 + ボディ）と混同しないよう注意すること。

### 5.4 GET /api/users の includeInactive 認可はコントローラー内手動チェック

`GET /api/users` は `SecurityConfig` の URL ベース制限では全認証済みユーザーにアクセスを許可している。しかし `includeInactive=true` の場合のみ、コントローラー内で `Authentication` のロールを手動チェックし、ADMIN でなければ `AccessDeniedException` をスローする設計になっている。この例外は `GlobalExceptionHandler.handleAccessDenied` が処理して 403 を返す。`@PreAuthorize` によるメソッドレベル制限とは異なり、同一メソッド内でパラメーターによって認可の有無が切り替わる点が特徴的。

### 5.5 @PreAuthorize による ADMIN 制限の対象エンドポイント

以下のエンドポイントはコントローラーに `@PreAuthorize("hasRole('ADMIN')")` が付与されており、STAFF ロールでアクセスすると Spring Security が `AccessDeniedException` をスローして 403 を返す。

- `POST /api/users`
- `PATCH /api/users/{id}`
- `DELETE /api/users/{id}`
- `PATCH /api/users/{id}/activate`
- `POST /api/users/{userId}/offices`
- `DELETE /api/users/{userId}/offices/{officeId}`

読み取り系エンドポイント（`GET /api/users`・`GET /api/users/{id}`・`GET /api/users/{userId}/offices`）は STAFF ロールでもアクセス可能。ただし `GET /api/users?includeInactive=true` は上記 5.4 の手動チェックにより STAFF には 403 が返る。

### 5.6 UserResponse.offices フィールドの @JsonInclude(NON_NULL)

`UserResponse.offices` には `@JsonInclude(JsonInclude.Include.NON_NULL)` が付与されている。ファクトリメソッドの使い分けに応じて以下のようにシリアライズ結果が変わる。

| 生成メソッド | offices の値 | JSON 出力 |
|---|---|---|
| `UserResponse.from(user)` | `null`（セットされない） | `offices` フィールド自体が出力されない |
| `UserResponse.fromWithOffices(user, emptyList)` | `[]`（空リスト） | `"offices": []` が出力される |
| `UserResponse.fromWithOffices(user, offices)` | 事業所リスト | `"offices": [...]` が出力される |

`GET /api/users/{id}` は `fromWithOffices` を使用するため、TC-USER-18 では事業所なしでも `"offices": []` が返る。

### 5.7 Spring Boot 4.0 でのアノテーションパッケージ変更

Spring Boot 4.0（Spring Framework 7.0）では以下のパッケージが変更されている。3.x のコード例をそのままコピーするとコンパイルエラーになる。

| アノテーション | Spring Boot 3.x | Spring Boot 4.0 |
|---|---|---|
| `@AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@MockitoBean`（旧 `@MockBean`） | `org.springframework.boot.test.mock.mockito` | `org.springframework.test.context.bean.override.mockito` |
