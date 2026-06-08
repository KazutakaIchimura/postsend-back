# Auth API テスト仕様書

## 1. 概要

### テスト対象

`AuthController` および Spring Security の `formLogin` / `logout` フィルターチェーンが処理する認証・認可エンドポイント全般。

| 対象ファイル | パス |
|---|---|
| テストクラス | `src/test/java/com/example/sendmail/controller/AuthControllerTest.java` |
| プロダクションコード | `src/main/java/com/example/sendmail/controller/AuthController.java` |
| セキュリティ設定 | `src/main/java/com/example/sendmail/config/SecurityConfig.java` |

### 目的

- ログイン・ログアウト・ユーザー情報取得・パスワード変更の各エンドポイントが正しい HTTP ステータスとレスポンスボディを返すことを保証する
- Spring Security の認証・認可フィルターが設計通りに動作することを確認する
- 異常系（資格情報誤り・無効アカウント・未認証アクセス・バリデーションエラー）の挙動を仕様として記録する

### 実行方法

```bash
# プロジェクトルートで実行
./mvnw test -Dtest=AuthControllerTest

# 特定のテストケースのみ実行
./mvnw test -Dtest="AuthControllerTest#should_returnOk_and_session_when_validCredentials"
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
| テスト用 DB | H2 インメモリ（MySQL モード）|

### テストクラスのアノテーション構成

```java
@SpringBootTest
@AutoConfigureMockMvc  // org.springframework.boot.webmvc.test.autoconfigure
@DisplayName("AuthController 統合テスト")
class AuthControllerTest { ... }
```

`@SpringBootTest` により実際の `SecurityFilterChain` を起動し、Spring Security の認証・認可フロー全体を通したテストを実現している。

### モック方針

| コンポーネント | 方針 | 理由 |
|---|---|---|
| `StaffRepository` | `@MockitoBean` でモック | DB 接続なしにインメモリで完結させる |
| `PasswordEncoder`（BCrypt） | Spring Context の実物を `@Autowired` | 実際のハッシュで認証テストの信頼性を確保 |
| `DatabaseMigrator` | モックせず起動（エラー無視） | `setContinueOnError=true` で H2 上の MySQL 固有構文エラーを無視 |
| メール送信等の外部サービス | 本テストの対象外 | 認証フローにのみ焦点を当てる |

### テストフィクスチャ

`@BeforeEach` で以下の Staff オブジェクトとスタブを初期化する。

| 変数 | メール | `isActive` | 用途 |
|---|---|---|---|
| `activeStaff` | `active@example.com` | `true` | 正常ログイン・認証済みテスト |
| `inactiveStaff` | `inactive@example.com` | `false` | 無効アカウントテスト |
| `RAW_PASSWORD` | `password123` | — | 共通テストパスワード |

---

## 3. テストケース一覧表

| TC番号 | エンドポイント | HTTP | シナリオ | 前提条件 | 期待 HTTP ステータス |
|---|---|---|---|---|---|
| TC-01 | `/api/auth/login` | POST | 正常ログイン | 有効なメール・正しいパスワード | 200 OK |
| TC-02 | `/api/auth/login` | POST | パスワード誤り | 正しいメール・誤ったパスワード | 401 Unauthorized |
| TC-03 | `/api/auth/login` | POST | メール未登録 | 存在しないメールアドレス | 401 Unauthorized |
| TC-04 | `/api/auth/login` | POST | 無効アカウント | `isActive=false` のアカウント | 401 Unauthorized |
| TC-05 | `/api/auth/login` | POST | メール空欄 | `username=""` | 401 Unauthorized |
| TC-06 | `/api/auth/login` | POST | パスワード空欄 | `password=""` | 401 Unauthorized |
| TC-07 | `/api/auth/logout` | POST | 正常ログアウト | 認証済みセッションあり | 200 OK |
| TC-08 | `/api/auth/logout` | POST | 未認証ログアウト | セッションなし | 200 OK |
| TC-09 | `/api/auth/me` | GET | 認証済みユーザー情報取得 | 有効なセッションあり | 200 OK |
| TC-10 | `/api/auth/me` | GET | 未認証アクセス | セッションなし | 401 Unauthorized |
| TC-10b | `/api/auth/me` | GET | ログアウト後のセッション再利用 | ログアウト済みセッション | 401 Unauthorized |
| TC-11 | `/api/auth/password/change` | POST | 正常パスワード変更 | 認証済み・8文字以上の新パスワード | 200 OK |
| TC-11b | `/api/auth/password/change` | POST | パスワード短すぎ | 認証済み・8文字未満 | 400 Bad Request |
| TC-11c | `/api/auth/password/change` | POST | パスワード null | 認証済み・`newPassword: null` | 400 Bad Request |
| TC-12 | `/api/auth/password/change` | POST | 未認証でパスワード変更 | セッションなし | 401 Unauthorized |
| TC-13 | `/api/staffs` | GET | USER ロールでの認可拒否 | `@WithMockUser(roles="USER")` | 403 Forbidden |
| TC-14 | `/api/staffs` | GET | ADMIN ロールでの認可通過 | `@WithMockUser(roles="ADMIN")` | 403 以外 |

---

## 4. 各テストケースの詳細

### TC-01: 正常ログイン

**シナリオ**: 有効なメールと正しいパスワードでログインすると 200 OK が返り、セッションが生成される。

**リクエスト**

```
POST /api/auth/login
Content-Type: application/x-www-form-urlencoded

username=active@example.com&password=password123
```

**期待レスポンス**

```
HTTP/1.1 200 OK
Content-Type: application/json

{"message": "ログイン成功"}
```

**確認ポイント**

- ステータスコード: `200 OK`
- レスポンスボディ: `$.message == "ログイン成功"`
- セッション: `request.getSession(false) != null`（セッションが生成されている）

---

### TC-02: パスワード誤り

**シナリオ**: 登録済みメールに誤ったパスワードを送信すると 401 が返る。

**リクエスト**

```
POST /api/auth/login
Content-Type: application/x-www-form-urlencoded

username=active@example.com&password=wrongpassword
```

**期待レスポンス**

```
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{"message": "メールアドレスまたはパスワードが違います"}
```

**確認ポイント**

- ステータスコード: `401 Unauthorized`
- レスポンスボディ: `$.message == "メールアドレスまたはパスワードが違います"`

---

### TC-03: 存在しないメール

**シナリオ**: 未登録のメールアドレスでログインを試みると 401 が返る。

**リクエスト**

```
POST /api/auth/login
Content-Type: application/x-www-form-urlencoded

username=notexist@example.com&password=password123
```

**期待レスポンス**

```
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{"message": "メールアドレスまたはパスワードが違います"}
```

**確認ポイント**

- ステータスコード: `401 Unauthorized`
- エラーメッセージは TC-02 と同一（メール不存在か誤パスワードかを区別しない）

---

### TC-04: 無効アカウント（`isActive=false`）

**シナリオ**: `isActive=false` のアカウントで正しいパスワードを送信しても 401 が返る。

**リクエスト**

```
POST /api/auth/login
Content-Type: application/x-www-form-urlencoded

username=inactive@example.com&password=password123
```

**期待レスポンス**

```
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{"message": "メールアドレスまたはパスワードが違います"}
```

**確認ポイント**

- ステータスコード: `401 Unauthorized`
- `UserDetailsService` が `isActive=false` の場合に `UsernameNotFoundException` をスロー → failureHandler が 401 を返す
- エラーメッセージは TC-02・TC-03 と同一（セキュリティ上、理由を開示しない）

---

### TC-05: メール空欄

**シナリオ**: `username` に空文字を送信すると 401 が返る。

**リクエスト**

```
POST /api/auth/login
Content-Type: application/x-www-form-urlencoded

username=&password=password123
```

**期待レスポンス**

```
HTTP/1.1 401 Unauthorized
```

**確認ポイント**

- ステータスコード: `401 Unauthorized`
- `formLogin` エンドポイントは `@Valid` が効かないため、空文字は Spring Security の認証失敗として扱われる

---

### TC-06: パスワード空欄

**シナリオ**: `password` に空文字を送信すると 401 が返る。

**リクエスト**

```
POST /api/auth/login
Content-Type: application/x-www-form-urlencoded

username=active@example.com&password=
```

**期待レスポンス**

```
HTTP/1.1 401 Unauthorized
```

**確認ポイント**

- ステータスコード: `401 Unauthorized`
- TC-05 と同様に、formLogin では Bean Validation が機能しない

---

### TC-07: 認証済みでログアウト

**シナリオ**: ログインして取得したセッションでログアウトすると 200 OK が返り、セッションが無効化される。

**前提**: TC-01 相当のログインでセッションを取得済み

**リクエスト**

```
POST /api/auth/logout
Cookie: JSESSIONID=<valid-session-id>
```

**期待レスポンス**

```
HTTP/1.1 200 OK
Content-Type: application/json

{"message": "ログアウトしました"}
```

**確認ポイント**

- ステータスコード: `200 OK`
- レスポンスボディ: `$.message == "ログアウトしました"`
- セッション無効化の確認は TC-10b で行う

---

### TC-08: 未認証でログアウト

**シナリオ**: セッションなしでログアウトエンドポイントを呼び出しても 200 OK が返る。

**リクエスト**

```
POST /api/auth/logout
```

**期待レスポンス**

```
HTTP/1.1 200 OK
Content-Type: application/json

{"message": "ログアウトしました"}
```

**確認ポイント**

- ステータスコード: `200 OK`
- Spring Security の logout フィルターは認証状態に関わらずリクエストを処理し、`logoutSuccessHandler` が応答する
- この挙動は設計上の仕様（「未認証なら 401 を返す」設計ではない）

---

### TC-09: 認証済みでユーザー情報取得

**シナリオ**: ログイン済みセッションで `/api/auth/me` を呼び出すと、ログイン中ユーザーの情報が返る。

**前提**: TC-01 相当のログインでセッションを取得済み

**リクエスト**

```
GET /api/auth/me
Cookie: JSESSIONID=<valid-session-id>
```

**期待レスポンス**

```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 1,
  "email": "active@example.com",
  "name": "有効スタッフ",
  "role": "USER",
  "isActive": true,
  "forcePasswordChange": false
}
```

**確認ポイント**

- ステータスコード: `200 OK`
- `$.id == 1`
- `$.email == "active@example.com"`
- `$.name == "有効スタッフ"`
- `$.role == "USER"`
- `$.isActive == true`
- `$.forcePasswordChange == false`

---

### TC-10: 未認証でユーザー情報取得

**シナリオ**: セッションなしで `/api/auth/me` を呼び出すと 401 が返る。

**リクエスト**

```
GET /api/auth/me
```

**期待レスポンス**

```
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{"message": "認証が必要です"}
```

**確認ポイント**

- ステータスコード: `401 Unauthorized`
- レスポンスボディ: `$.message == "認証が必要です"`

---

### TC-10b: ログアウト後のセッション再利用

**シナリオ**: ログアウト後に同一セッションで `/api/auth/me` を呼び出すと 401 が返る（セッション無効化の確認）。

**手順**

1. `POST /api/auth/login` でセッション取得
2. `POST /api/auth/logout` でログアウト（セッション無効化）
3. 同セッションで `GET /api/auth/me` を呼び出す

**期待レスポンス**

```
HTTP/1.1 401 Unauthorized
```

**確認ポイント**

- ステータスコード: `401 Unauthorized`
- ログアウト後にセッションが実際に無効化されていることを確認する

---

### TC-11: 正常なパスワード変更

**シナリオ**: 認証済みで 8 文字以上の新パスワードを送信すると 200 OK が返り、`StaffRepository.save` が呼ばれる。

**前提**: TC-01 相当のログインでセッションを取得済み

**リクエスト**

```
POST /api/auth/password/change
Cookie: JSESSIONID=<valid-session-id>
Content-Type: application/json

{"newPassword": "newSecurePass1"}
```

**期待レスポンス**

```
HTTP/1.1 200 OK
```

**確認ポイント**

- ステータスコード: `200 OK`
- `StaffRepository.save(any(Staff.class))` が呼び出されたこと（Mockito の `verify`）

---

### TC-11b: 新パスワードが 8 文字未満

**シナリオ**: 認証済みで 8 文字未満のパスワードを送信すると 400 Bad Request が返る。

**前提**: TC-01 相当のログインでセッションを取得済み

**リクエスト**

```
POST /api/auth/password/change
Cookie: JSESSIONID=<valid-session-id>
Content-Type: application/json

{"newPassword": "short"}
```

**期待レスポンス**

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "message": "入力値が不正です",
  "errors": {
    "newPassword": "<バリデーションメッセージ>"
  }
}
```

**確認ポイント**

- ステータスコード: `400 Bad Request`
- `$.message == "入力値が不正です"`
- `$.errors.newPassword` フィールドが存在すること（`@Size` バリデーションエラー）

---

### TC-11c: 新パスワードが null

**シナリオ**: 認証済みで `newPassword: null` を送信すると 400 Bad Request が返る。

**前提**: TC-01 相当のログインでセッションを取得済み

**リクエスト**

```
POST /api/auth/password/change
Cookie: JSESSIONID=<valid-session-id>
Content-Type: application/json

{"newPassword": null}
```

**期待レスポンス**

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "errors": {
    "newPassword": "<バリデーションメッセージ>"
  }
}
```

**確認ポイント**

- ステータスコード: `400 Bad Request`
- `$.errors.newPassword` フィールドが存在すること（`@NotBlank` バリデーションエラー）

---

### TC-12: 未認証でパスワード変更

**シナリオ**: セッションなしでパスワード変更エンドポイントを呼び出すと 401 が返る。

**リクエスト**

```
POST /api/auth/password/change
Content-Type: application/json

{"newPassword": "newSecurePass1"}
```

**期待レスポンス**

```
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{"message": "認証が必要です"}
```

**確認ポイント**

- ステータスコード: `401 Unauthorized`
- レスポンスボディ: `$.message == "認証が必要です"`

---

### TC-13: USER ロールで `/api/staffs` アクセス拒否

**シナリオ**: `ROLE_USER` を持つユーザーが `/api/staffs` にアクセスすると 403 Forbidden が返る。

**前提**: `@WithMockUser(roles = "USER")` による認証済み状態

**リクエスト**

```
GET /api/staffs
```

**期待レスポンス**

```
HTTP/1.1 403 Forbidden
Content-Type: application/json

{"message": "権限がありません"}
```

**確認ポイント**

- ステータスコード: `403 Forbidden`
- レスポンスボディ: `$.message == "権限がありません"`
- `SecurityConfig` で `/api/staffs/**` が `hasRole("ADMIN")` に制限されていることを確認

---

### TC-14: ADMIN ロールで `/api/staffs` アクセス通過

**シナリオ**: `ROLE_ADMIN` を持つユーザーが `/api/staffs` にアクセスすると認可が通過し、403 以外が返る。

**前提**: `@WithMockUser(roles = "ADMIN")` による認証済み状態

**リクエスト**

```
GET /api/staffs
```

**期待レスポンス**

```
HTTP/1.1 <403 以外>
```

**確認ポイント**

- ステータスコード: `403` ではないこと
- 実際のエンドポイント動作（リスト取得等）は `StaffControllerTest` で検証する
- このテストは認可チェックの通過のみを確認する

---

## 5. 発見した仕様メモ

### 5-1. formLogin では Bean Validation が効かない

`/api/auth/login` は `SecurityConfig` の `formLogin()` で処理されるため、`@RequestBody @Valid` のようなコントローラー側のバリデーションは機能しない。空文字・null の `username` / `password` はいずれも Spring Security の認証失敗（`UsernameNotFoundException` / `BadCredentialsException`）として扱われ、`authenticationFailureHandler` が 401 を返す。

### 5-2. 未認証ログアウトは 200 を返す（設計上の仕様）

`/api/auth/logout` は Spring Security の `logoutFilter` が処理するため、認証状態に関わらずリクエストを受け入れ、`logoutSuccessHandler` が 200 を返す。この挙動は意図的な設計であり、TC-08 でその仕様を明示的にテストしている。冪等性の観点からも問題ない動作といえる。

### 5-3. 無効アカウントのエラーメッセージは意図的に曖昧

`isActive=false` のアカウントに対するエラーメッセージは「メールアドレスまたはパスワードが違います」であり、「アカウントが無効です」のような具体的な理由は返さない。これはセキュリティ上の考慮（存在するアカウントを攻撃者が特定できないようにする）による設計判断である。

### 5-4. MockMvc での JSESSIONID Cookie 確認の注意

MockMvc はテスト用の `MockHttpServletResponse` を使用するため、実際の HTTP レスポンスと異なり `Set-Cookie: JSESSIONID=...` が `response.getCookie("JSESSIONID")` で取得できない場合がある。セッション発行の確認には `result.getRequest().getSession(false) != null` を使用すること（TC-01 参照）。

### 5-5. パスワード変更エンドポイントの HTTP メソッドは POST

`AuthController.changePassword` は `@PostMapping("/password/change")` で定義されており、REST の慣例的な `PATCH` ではなく `POST` である。テスト・クライアント実装時は `POST` を使うこと。

### 5-6. Spring Boot 4.0 でのアノテーションパッケージ変更

Spring Boot 4.0（Spring Framework 7.0）では以下のパッケージが変更されている。3.x のコード例をそのままコピーするとコンパイルエラーになる。

| アノテーション | Spring Boot 3.x | Spring Boot 4.0 |
|---|---|---|
| `@AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@MockitoBean`（旧 `@MockBean`） | `org.springframework.boot.test.mock.mockito` | `org.springframework.test.context.bean.override.mockito` |

### 5-7. PasswordEncoder は実物（BCrypt）を使用

テスト内では `PasswordEncoder` をモックせず、Spring Context が生成した `BCryptPasswordEncoder` の実物を `@Autowired` している。これにより `passwordEncoder.encode(RAW_PASSWORD)` で生成したハッシュを Staff エンティティにセットし、実際の認証フローでパスワード照合が正しく行われることを確認している。モックに置き換えると認証テストの信頼性が低下するため注意。
