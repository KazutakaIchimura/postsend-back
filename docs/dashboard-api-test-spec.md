# Dashboard API テスト仕様書

## 1. 概要

### テスト対象

`DashboardController` が処理するダッシュボード情報取得エンドポイント、および Spring Security の認証・認可フィルターチェーン。

| 対象ファイル | パス |
|---|---|
| テストクラス | `src/test/java/com/example/sendmail/controller/DashboardControllerTest.java` |
| プロダクションコード | `src/main/java/com/example/sendmail/controller/DashboardController.java` |
| サービス | `src/main/java/com/example/sendmail/service/DashboardService.java` |
| リポジトリ（モック対象） | `src/main/java/com/example/sendmail/repository/MailSendRepository.java` |
| リポジトリ（モック対象） | `src/main/java/com/example/sendmail/repository/StaffRepository.java` |
| セキュリティ設定 | `src/main/java/com/example/sendmail/config/SecurityConfig.java` |

### 目的

- ダッシュボード API（`GET /api/dashboard`）が正しいレスポンス構造とフィールド値を返すことを検証する
- 認証必須エンドポイントに対して未認証アクセスが適切に拒否されることを検証する
- セッション無効化後のアクセスが 401 を返すことを検証する
- レスポンスフィールドの型・形式・整合性（`overdueCount` と `overdueMonths` の合計一致など）を網羅的に検証する

### 実行方法

```bash
# プロジェクトルートで実行
./mvnw test -Dtest=DashboardControllerTest

# 特定のテストケースのみ実行
./mvnw test -Dtest="DashboardControllerTest#should_returnFullDashboardData_when_allDataExists"
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
| JSON アサーション | MockMvc `jsonPath` + Hamcrest `matchesPattern` |
| 認証ヘルパー | `@WithMockUser`（正常系）、実 `formLogin`/`logout`（TC-DASH-07） |

### テストクラスのアノテーション構成

```java
@SpringBootTest
@AutoConfigureMockMvc  // org.springframework.boot.webmvc.test.autoconfigure
@DisplayName("DashboardController 統合テスト")
class DashboardControllerTest { ... }
```

`@SpringBootTest` により実際の `SecurityFilterChain` を起動し、Spring Security の認証・認可フロー全体を通したテストを実現している。

### モック方針

| コンポーネント | 方針 | 理由 |
|---|---|---|
| `MailSendRepository` | `@MockitoBean` でモック化 | DB 接続なしにダッシュボードロジックを検証するため |
| `StaffRepository` | `@MockitoBean` でモック化 | TC-DASH-07 のログインフローで必要。正常系では不使用 |
| `PasswordEncoder` | 実物を使用 | Spring Context が生成した BCrypt エンコーダーをそのまま利用 |
| `DashboardService` | モックなし（実物） | Service のロジックごと統合テストとして検証するため |

### デフォルトスタブ（`@BeforeEach`）

各テストは必要なスタブのみ上書きし、残りはデフォルト値が使われる。

| スタブ対象メソッド | デフォルト戻り値 |
|---|---|
| `countByStatus(any())` | `0L` |
| `countByStatusAndSendMonth(any(), any())` | `0L` |
| `countGroupedByMonthBefore(any(), any())` | `Collections.emptyList()` |
| `findRecentSentHistory(any(), any(Pageable.class))` | `Collections.emptyList()` |
| `staffRepository.findByEmail(anyString())` | `Optional.empty()` |
| `staffRepository.findByEmail(LOGIN_EMAIL)` | 有効スタッフの `Optional.of(staff)` |

---

## 3. テストケース一覧

| TC 番号 | カテゴリ | テスト名（概要） | 認証方法 | 期待ステータス |
|---|---|---|---|---|
| TC-DASH-01 | 正常系 | 全データあり（標準正常系） | `@WithMockUser` | 200 OK |
| TC-DASH-02 | 正常系 | データなし（全ゼロ・空配列・currentMonth 形式確認） | `@WithMockUser` | 200 OK |
| TC-DASH-03 | 正常系 | 今月 PENDING のみ・過去延滞なし | `@WithMockUser` | 200 OK |
| TC-DASH-04 | 正常系 | 送付履歴 5 件上限・PageRequest 検証 | `@WithMockUser` | 200 OK |
| TC-DASH-05 | 正常系 | overdueCount と overdueMonths 合計の整合性 | `@WithMockUser` | 200 OK |
| TC-DASH-06 | 認証・認可 | 未認証アクセス → 401 | なし | 401 Unauthorized |
| TC-DASH-07 | 認証・認可 | ログイン→ログアウト→アクセス → 401 | 実 formLogin | 401 Unauthorized |
| TC-DASH-08 | 正常系 | sendType=MONITORING が "MONITORING" で返る | `@WithMockUser` | 200 OK |
| TC-DASH-09 | 正常系 | sentAt フィールドの存在・非 null 確認 | `@WithMockUser` | 200 OK |
| TC-DASH-10 | 正常系 | recentHistory の sentAt 降順維持 | `@WithMockUser` | 200 OK |
| TC-DASH-11 | 正常系 | overdueMonths 月昇順（3件）・合計一致 | `@WithMockUser` | 200 OK |
| TC-DASH-12 | 正常系 | currentMonth が `\d{4}-\d{2}` 形式 | `@WithMockUser` | 200 OK |
| TC-DASH-13 | 正常系 | recentHistory 全フィールド欠落なし | `@WithMockUser` | 200 OK |
| TC-DASH-14 | 正常系 | 送付履歴 1 件（境界値） | `@WithMockUser` | 200 OK |
| TC-DASH-15 | 正常系 | 完全空状態で全フィールドを一括確認 | `@WithMockUser` | 200 OK |

---

## 4. テストケース詳細

---

### TC-DASH-01: 全データあり（標準正常系）

**目的**

ダッシュボード API が全フィールドを正しく組み立てて返すことを、代表的なデータセットで確認する。

**前提条件**

- `@WithMockUser` により認証済み
- `pendingCount=3`、`overdueCount=2`（2025-12 × 1件、2026-01 × 1件）、`sentThisMonthCount=1`
- `recentHistory` に 1 件スタブ（`id=10`、`officeName=東京事務所`、`sendType=PLAN`）

**入力データ（モック設定値）**

| スタブ | 設定値 |
|---|---|
| `countByStatus(PENDING)` | `3L` |
| `countByStatusAndSendMonth(SENT, 今月)` | `1L` |
| `countGroupedByMonthBefore(PENDING, 今月)` | `[{2025-12, 1}, {2026-01, 1}]` |
| `findRecentSentHistory(SENT, PageRequest.of(0,5))` | `[{id=10, 東京事務所, 山田太郎, PLAN, 2026-06-01T10:00}]` |

**期待結果**

HTTP 200 OK + `application/json`

```json
{
  "pendingCount": 3,
  "overdueCount": 2,
  "sentThisMonthCount": 1,
  "currentMonth": "2026-06",
  "overdueMonths": [
    { "month": "2025-12", "count": 1 },
    { "month": "2026-01", "count": 1 }
  ],
  "recentHistory": [{
    "id": 10,
    "officeName": "東京事務所",
    "userName": "山田太郎",
    "sendType": "PLAN",
    "sentAt": <non-null>
  }]
}
```

**検証項目**

- `$.pendingCount == 3`
- `$.overdueCount == 2`
- `$.sentThisMonthCount == 1`
- `$.currentMonth` が今月の `yyyy-MM` 形式
- `$.overdueMonths` が長さ 2 の配列
- `$.overdueMonths[0].month == "2025-12"` / `.count == 1`
- `$.overdueMonths[1].month == "2026-01"` / `.count == 1`
- `$.recentHistory[0].id == 10`、`.officeName == "東京事務所"`、`.sendType == "PLAN"`
- `$.recentHistory[0].sentAt` が存在し null でない

---

### TC-DASH-02: データなし（全ゼロ・空配列・currentMonth 形式確認）

**目的**

全件ゼロ・全リスト空の状態で API が正常レスポンスを返し、フィールドが欠落しないことを確認する。`currentMonth` が null でなく `yyyy-MM` 形式で返ることも確認する。

**前提条件**

- `@WithMockUser` により認証済み
- 全スタブはデフォルト値（`@BeforeEach` の設定をそのまま使用）

**入力データ（モック設定値）**

追加スタブなし。

**期待結果**

HTTP 200 OK:

```json
{
  "pendingCount": 0,
  "overdueCount": 0,
  "sentThisMonthCount": 0,
  "currentMonth": "2026-06",
  "overdueMonths": [],
  "recentHistory": []
}
```

**検証項目**

- `$.pendingCount == 0`、`$.overdueCount == 0`、`$.sentThisMonthCount == 0`
- `$.currentMonth` が存在・非空・今月の `yyyy-MM` 形式
- `$.overdueMonths` が長さ 0 の空配列（`null` でないこと）
- `$.recentHistory` が長さ 0 の空配列（`null` でないこと）

---

### TC-DASH-03: 今月 PENDING のみ・過去延滞なし

**目的**

`overdueCount=0` かつ `overdueMonths` が空の場合に、延滞関連フィールドが正しくゼロ・空で返ること、および `currentMonth` と `sentThisMonthCount` が正しく返ることを確認する。

**前提条件**

- `@WithMockUser` により認証済み
- `pendingCount=2`（今月分のみ存在）
- `overdueMonths` は空（`@BeforeEach` デフォルトのまま）

**入力データ（モック設定値）**

| スタブ | 設定値 |
|---|---|
| `countByStatus(PENDING)` | `2L` |

**期待結果**

HTTP 200 OK:

- `$.pendingCount == 2`
- `$.overdueCount == 0`
- `$.sentThisMonthCount == 0`
- `$.currentMonth` が今月の形式
- `$.overdueMonths` が空配列

---

### TC-DASH-04: 送付履歴 5 件上限・PageRequest 検証

**目的**

`recentHistory` がちょうど 5 件返ること、および内部で `PageRequest.of(0, 5)` が使用されていることを `verify` で確認する。

**前提条件**

- `@WithMockUser` により認証済み
- `recentHistory` に 5 件スタブ

**入力データ（モック設定値）**

| スタブ | 設定値 |
|---|---|
| `countByStatusAndSendMonth(SENT, 今月)` | `20L` |
| `findRecentSentHistory(SENT, Pageable)` | `[{id=1,...},{id=2,...},{id=3,...},{id=4,...},{id=5,...}]` |

**期待結果**

HTTP 200 OK:

- `$.recentHistory` の長さが `5`
- `$.recentHistory[0].id == 1`、`$.recentHistory[4].id == 5`

**検証項目**

- `$.recentHistory.length() == 5`
- `verify(mailSendRepository).findRecentSentHistory(eq(SENT), eq(PageRequest.of(0, 5)))` が 1 回呼ばれること

---

### TC-DASH-05: overdueCount と overdueMonths 合計の整合性

**目的**

`overdueCount` の値が `overdueMonths` の各 `count` 合計（stream sum）と一致していることを検証する。

**前提条件**

- `@WithMockUser` により認証済み
- `overdueMonths` を 2 件スタブ（count の合計が `overdueCount` に等しい）

**入力データ（モック設定値）**

| スタブ | 設定値 |
|---|---|
| `countByStatus(PENDING)` | `7L` |
| `countGroupedByMonthBefore(PENDING, 今月)` | `[{2026-03, 3}, {2026-04, 2}]` |

**期待結果**

HTTP 200 OK:

- `$.overdueCount == 5`（`3 + 2`）
- `$.overdueMonths.length() == 2`
- `$.overdueMonths[0].count == 3`
- `$.overdueMonths[1].count == 2`

---

### TC-DASH-06: 未認証アクセス → 401

**目的**

認証なしで `GET /api/dashboard` にアクセスした場合、401 と規定のエラーメッセージが返ることを確認する。

**前提条件**

- `@WithMockUser` なし（未認証状態）

**期待結果**

HTTP 401 Unauthorized + `application/json`:

```json
{ "message": "認証が必要です" }
```

---

### TC-DASH-07: ログイン→ログアウト→アクセス → 401（セッション無効化確認）

**目的**

有効なセッションでログアウト後、同一セッションでダッシュボードにアクセスしても 401 が返ること（セッション無効化）を実際のフローで確認する。

**前提条件**

- `@WithMockUser` を使用せず、実際の `formLogin` フローを使用
- `StaffRepository` に有効スタッフがスタブ済み（`@BeforeEach`）

**テストフロー**

```
1. POST /api/auth/login（formLogin）→ 200 OK + セッション取得
2. POST /api/auth/logout（同セッション）→ 200 OK + セッション無効化
3. GET /api/dashboard（無効化されたセッション）→ 401 Unauthorized
```

**期待結果**

- ステップ 1: 200 OK
- ステップ 2: 200 OK
- ステップ 3: 401 Unauthorized + `{"message":"認証が必要です"}`

---

### TC-DASH-08: sendType=MONITORING が "MONITORING" で返る

**目的**

`SendType.MONITORING` が JSON シリアライズ時に文字列 `"MONITORING"` として正しく出力されることを確認する（`PLAN` のみの確認では不十分なため専用テストを設ける）。

**前提条件**

- `@WithMockUser` により認証済み
- `recentHistory` に `sendType=MONITORING` の 1 件スタブ

**入力データ（モック設定値）**

| スタブ | 設定値 |
|---|---|
| `findRecentSentHistory(SENT, Pageable)` | `[{id=20, 大阪事務所, 鈴木花子, MONITORING, 2026-05-30T09:00}]` |

**期待結果**

HTTP 200 OK:

- `$.recentHistory[0].sendType == "MONITORING"`

---

### TC-DASH-09: sentAt フィールドの存在・非 null 確認（専用テスト）

**目的**

`recentHistory` の各要素に `sentAt` フィールドが存在し、null でないことを単独で確認する。`sentAt` の具体的な形式（配列 or ISO 文字列）は Jackson 設定に依存するため、存在確認のみとする。

**前提条件**

- `@WithMockUser` により認証済み
- `sentAt` を明示的に設定した 1 件スタブ（`LocalDateTime.of(2026, 6, 1, 15, 30, 0)`）

**期待結果**

HTTP 200 OK:

- `$.recentHistory[0].sentAt` が存在し null でない

---

### TC-DASH-10: recentHistory の sentAt 降順維持

**目的**

リポジトリから降順で受け取った `recentHistory` の順序がサービス層・コントローラー層で変更されないことを確認する（`DashboardService` は `toList()` で順序を維持するはずだが、回帰テストとして保持する）。

**前提条件**

- `@WithMockUser` により認証済み
- 2 件スタブを降順（新 → 旧）で設定

**入力データ（モック設定値）**

| スタブ | 設定値 |
|---|---|
| `findRecentSentHistory(SENT, Pageable)` | `[{id=100, sentAt=2026-06-02}, {id=99, sentAt=2026-05-15}]`（降順） |

**期待結果**

HTTP 200 OK:

- `$.recentHistory[0].id == 100`（最新）
- `$.recentHistory[1].id == 99`（古い方）

---

### TC-DASH-11: overdueMonths 月昇順（3件）・overdueCount 合計一致

**目的**

`overdueMonths` が月の昇順で返り、3件の `count` 合計が `overdueCount` と一致することを確認する。

**前提条件**

- `@WithMockUser` により認証済み
- `countGroupedByMonthBefore` に昇順 3 件スタブ

**入力データ（モック設定値）**

| スタブ | 設定値 |
|---|---|
| `countGroupedByMonthBefore(PENDING, 今月)` | `[{2026-01, 2}, {2026-02, 1}, {2026-03, 3}]` |

**期待結果**

HTTP 200 OK:

- `$.overdueMonths.length() == 3`
- `$.overdueMonths[0].month == "2026-01"` / `.count == 2`
- `$.overdueMonths[1].month == "2026-02"` / `.count == 1`
- `$.overdueMonths[2].month == "2026-03"` / `.count == 3`
- `$.overdueCount == 6`（`2 + 1 + 3`）

---

### TC-DASH-12: currentMonth が `yyyy-MM` 形式

**目的**

`currentMonth` フィールドが `yyyy-MM` 形式の文字列であることを正規表現で検証する。具体的な年月値をハードコードすると翌月以降の CI で失敗するため、形式のみを確認する。

**前提条件**

- `@WithMockUser` により認証済み
- デフォルトスタブのまま

**期待結果**

HTTP 200 OK:

- `$.currentMonth` が正規表現 `\d{4}-\d{2}` にマッチする

---

### TC-DASH-13: recentHistory 全フィールド欠落なし

**目的**

`recentHistory` の 1 要素に `id`・`officeName`・`userName`・`sendType`・`sentAt` の全 5 フィールドが揃って返ることを確認する。フィールド名の typo や DTO のマッピング漏れを検出する。

**前提条件**

- `@WithMockUser` により認証済み
- 全フィールドを設定した 1 件スタブ（`id=50`、`札幌事務所`、`北田次郎`、`MONITORING`）

**期待結果**

HTTP 200 OK:

- `$.recentHistory[0].id == 50`
- `$.recentHistory[0].officeName == "札幌事務所"`
- `$.recentHistory[0].userName == "北田次郎"`
- `$.recentHistory[0].sendType == "MONITORING"`
- `$.recentHistory[0].sentAt` が存在し null でない

---

### TC-DASH-14: 送付履歴 1 件（境界値）

**目的**

`recentHistory` が最小の 1 件の場合（境界値）に正しく動作することを確認する。配列インデックス `[0]` のアクセスが成功し、フィールドが正しく返ること。

**前提条件**

- `@WithMockUser` により認証済み
- `recentHistory` に 1 件スタブ

**期待結果**

HTTP 200 OK:

- `$.recentHistory.length() == 1`
- `$.recentHistory[0].id == 1`、`.officeName == "事務所1"`、`.sendType == "PLAN"`

---

### TC-DASH-15: 完全空状態で全フィールドを一括確認

**目的**

全データが空・ゼロの状態でレスポンスの全フィールドを一括確認し、フィールド欠落や型不正がないことを確認する。TC-DASH-02 が `currentMonth` 形式に特化しているのに対し、本テストは全 6 フィールドを同時に検証するため補完的な位置付けとなる。

**前提条件**

- `@WithMockUser` により認証済み
- 全スタブはデフォルト値

**期待結果**

HTTP 200 OK:

- 全数値フィールドが `0`
- `currentMonth` が存在し `\d{4}-\d{2}` 形式
- `overdueMonths`・`recentHistory` が共に長さ 0 の配列

---

## 5. テスト設計上の注意点

### 5.1 Mockito の Stream 遅延評価問題

`when(...).thenReturn(list)` に渡すリストをヘルパーメソッドで生成する場合、`thenReturn()` の引数内で呼び出すと `UnfinishedStubbingException` が発生することがある。

**誤った例（NG）:**

```java
// NG: thenReturn の引数内でモック生成ヘルパーを呼ぶと例外が起きる場合がある
when(mailSendRepository.findRecentSentHistory(any(), any()))
        .thenReturn(buildRecentHistoryProjections(5));
```

**正しい例（OK）:**

```java
// OK: 先に変数へ代入してから thenReturn に渡す
List<MailSendRepository.RecentSentHistoryProjection> items = buildRecentHistoryProjections(5);
when(mailSendRepository.findRecentSentHistory(any(), any()))
        .thenReturn(items);
```

また、ヘルパーメソッド内での Mockito の `when()` 呼び出しは `for` ループで逐次実行し、Stream（`mapToObj` など）の遅延評価とは組み合わせない。

### 5.2 sentAt の JSON シリアライズ形式

`LocalDateTime` は Jackson のデフォルト設定では **数値配列**形式でシリアライズされる。

```json
"sentAt": [2026, 6, 1, 10, 0]
```

`jsonPath` で特定の年月日時刻文字列を期待するアサーションを書くと形式が異なる場合に誤検知するため、本テストでは `exists()` / `isNotEmpty()` による存在確認のみとしている。プロジェクトで `spring.jackson.serialization.write-dates-as-timestamps=false` を設定した場合は ISO-8601 文字列形式になるため、設定変更時はアサーション方針を見直すこと。

### 5.3 `@WithMockUser` と実 formLogin の使い分け

| テストケース | 認証方法 | 理由 |
|---|---|---|
| TC-DASH-01〜05、08〜15 | `@WithMockUser` | セッション管理を検証しない正常系・機能テストで十分 |
| TC-DASH-06 | なし（未認証） | 認証なしアクセスの 401 を確認するため |
| TC-DASH-07 | 実 `formLogin` + `logout` | セッション無効化という Spring Security の実動作を検証するため、実フローが必要 |

`@WithMockUser` はセキュリティフィルターをバイパスしてモックユーザーを SecurityContext に注入するため、ログイン・ログアウト後のセッション状態遷移を検証できない。TC-DASH-07 のみ `MockMvc` の `post("/api/auth/login")` / `post("/api/auth/logout")` を使用する。

### 5.4 `overdueCount` と `overdueMonths` の整合性

`DashboardService` は `overdueCount` を `countGroupedByMonthBefore` の結果をストリームで sum して導出しており、独立したカウントクエリは発行していない。そのため TC-DASH-05・TC-DASH-11 では、`countGroupedByMonthBefore` のスタブに設定した件数の合計が `overdueCount` として返ることを検証している。

### 5.5 `PageRequest.of(0, 5)` の verify

TC-DASH-04 では、`recentHistory` が正確に `PageRequest.of(0, 5)` で取得されることを `verify` で確認する。`any(Pageable.class)` マッチャーでは取得件数の退行（例: 10 件に変更されても気づかない）を検知できないため、`eq(PageRequest.of(0, 5))` を使用する。

### 5.6 `currentMonth` の動的な値

`currentMonth` はサーバーの現在日時から生成されるため、具体的な年月値（例: `"2026-06"`）をハードコードすると翌月以降の CI 実行で失敗する。TC-DASH-12 と TC-DASH-15 では正規表現 `\d{4}-\d{2}` でマッチさせ、TC-DASH-01〜03 では `LocalDate.now().format(MONTH_FORMATTER)` で動的に期待値を生成する。
