# Tokyo Sandwich

[English](#english) · [中文](#中文) · [日本語](#日本語)

---

## English

A local-first Android app that brings your Tokyo utility bills and your own living costs together in
one place. No server, no cloud account: everything stays on your phone.

### Features

- **Utility bills, fetched for you**: monthly usage and cost from your own accounts at
    - TEPCO (くらしTEPCO web): you sign in on TEPCO's own page inside the app; no password is
      stored.
    - Tokyo Gas (myTOKYOGAS): saved ID and password, signs in automatically on the official page.
    - Tokyo Waterworks (東京都水道局アプリ): saved email and password, signs in automatically.
      Two-month billing periods are spread into calendar months (estimated).
- **Cost tree**: organise your costs like files and folders, add bills by hand (single months,
  ranges, or monthly recurring bills). Utility bills appear in the tree automatically.
- **Home board**: pick a month and arrange cards for utilities, single costs, or ring charts of a
  cost set. Drag to reorder.
- **Backup**: export / import your own data as a JSON file.
- English, 中文 and 日本語; light and dark theme.

### Principles

- No project server. Data comes only from the official services and from you.
- Credentials are encrypted with Android Keystore and sent only to the official service.
- Read-only access to your own data. Syncs only when you ask (login or the refresh button), never in
  the background.

### Build

Requires JDK 17 and the Android SDK (minSdk 34).

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Kotlin · Jetpack Compose · Material 3 · OkHttp · kotlinx.serialization · DataStore · Room

> The official services' web APIs are not public and may change at any time. This is an unofficial
> project, not affiliated with TEPCO, Tokyo Gas or the Bureau of Waterworks.

To young people living in Tokyo: I hope this project saves you from clicking through messy websites
and helps you manage your finances better. If you find it useful, please give it a ⭐ — forks, issues
and PRs are welcome too.

---

## 中文

一个完全在本机运行的 Android App，把东京的电、气、水账单和你自己的生活开销整理在一起。没有服务器，没有云端账户，数据只在你的手机上。

### 功能

- **自动获取水电气账单**：从你自己的账户取回每月用量和金额
    - 东京电力（くらしTEPCO web）：在 App 内打开官方页面自己登录，不保存密码。
    - 东京燃气（myTOKYOGAS）：保存 ID 和密码，在官方页面上自动登录。
    - 东京都水道（東京都水道局アプリ）：保存邮箱和密码，自动登录。两个月一期的账单按天折算成日历月（估算）。
- **费用树**：像文件夹一样整理开销，手动记账（单月、多段月份、每月固定）。水电气账单自动出现在树里。
- **主页看板**：选一个月份，自由摆放水电气、单项费用或费用集环形图的卡片，拖动排序。
- **备份**：把自己的数据导出 / 导入为 JSON 文件。
- 支持中文、日文、英文，浅色和深色主题。

### 原则

- 没有项目服务器，数据只来自官方服务和你自己的输入。
- 账号密码用 Android Keystore 加密，只发送给对应的官方服务。
- 只读取你自己的数据；只在你要求时同步（登录或点刷新），没有后台任务。

### 构建

需要 JDK 17 和 Android SDK（minSdk 34）。

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

> 官方服务的网页接口并不公开，随时可能变化。本项目为非官方项目，与东京电力、东京燃气、东京都水道局无关。

对于那些在东京生活的年轻人们，希望这个项目省去你访问乱七八糟网页的时间，让你能更有效的管理自己的财务状况，如果你觉得有用，请给我一个
star，以及 fork、issue、pr 等。

---

## 日本語

東京の電気・ガス・水道の請求と、自分の生活費をひとつにまとめるローカルファーストの Android
アプリです。サーバーもクラウドアカウントもなく、データはすべて端末の中にだけ保存されます。

### 機能

- **光熱費を自動取得**：自分のアカウントから毎月の使用量と料金を取得
    - 東京電力（くらしTEPCO web）：アプリ内で東京電力の公式ページから自分でログイン。パスワードは保存しません。
    - 東京ガス（myTOKYOGAS）：ID とパスワードを保存し、公式ページで自動ログイン。
    - 東京都水道局（東京都水道局アプリ）：メールアドレスとパスワードを保存し、自動ログイン。2
      か月ごとの請求を日割りで暦月に換算します（推定）。
- **費用ツリー**：フォルダのように生活費を整理し、手入力で記録（単月・複数期間・毎月固定）。光熱費は自動でツリーに入ります。
- **ホーム**：月を選び、光熱費・各費用・費用グループのドーナツグラフのカードを自由に並べられます。
- **バックアップ**：自分のデータを JSON ファイルでエクスポート / インポート。
- 日本語・中国語・英語、ライト / ダークテーマに対応。

### 方針

- プロジェクトのサーバーはありません。データは公式サービスと自分の入力からのみ。
- 認証情報は Android Keystore で暗号化し、公式サービスにのみ送信します。
- 自分のデータの読み取りのみ。同期はログインか更新ボタンを押したときだけで、バックグラウンド処理はありません。

### ビルド

JDK 17 と Android SDK（minSdk 34）が必要です。

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

> 公式サービスの Web API は公開されておらず、予告なく変わる可能性があります。本プロジェクトは非公式で、東京電力・東京ガス・東京都水道局とは関係ありません。

東京で暮らす若い皆さんへ。このプロジェクトでごちゃごちゃした Web
サイトを巡る手間を省き、自分のお金をもっと上手に管理できますように。役に立ったら ⭐
をお願いします。fork・issue・PR も大歓迎です。
