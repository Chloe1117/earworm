# Android 출시 (TWA · Bubblewrap)

`index.html` 을 그대로 감싸 Google Play 에 올린다. 네이티브 코드는 없다.

## 0. 먼저 막히는 곳 — 도메인

TWA 는 `https://<도메인>/.well-known/assetlinks.json` 으로 앱 소유를 증명한다.
**도메인 루트**여야 하므로 `chloe1117.github.io/earworm/` 같은 프로젝트 페이지로는 안 된다.

- 권장: 커스텀 도메인(예: `hookline.app`)을 GitHub Pages 에 연결하고, 저장소 루트에 `.well-known/assetlinks.json` 을 둔다.
- 대안: `chloe1117.github.io` 사용자 페이지 저장소 루트에 assetlinks 를 둔다.

검증이 실패하면 앱 상단에 주소창이 뜬다(동작은 한다). 심사 반려 사유는 아니지만 유료 앱으로는 품질 문제다.

## 1. 빌드

```bash
npm i -g @bubblewrap/cli
# twa-manifest.json 의 REPLACE_WITH_YOUR_DOMAIN 을 실제 도메인으로 바꾼 뒤
cd android
bubblewrap init --manifest https://<도메인>/manifest.webmanifest   # 또는 기존 twa-manifest.json 사용
bubblewrap build        # → app-release-bundle.aab
```

`android.keystore` 는 `.gitignore` 에 걸려 있다. **분실하면 업데이트 불가**이므로 별도 보관한다.

## 2. Play Console

1. 앱 만들기 → **유료** 선택 (나중에 무료→유료 전환 불가, 유료→무료만 가능)
2. 결제 프로필(판매자 계정) 연결
3. **앱 무결성 → 앱 서명** 에서 SHA-256 복사 → `assetlinks.template.json` 에 넣어 `/.well-known/assetlinks.json` 으로 배포
4. 비공개 테스트 트랙에 `.aab` 업로드 → 테스터 12명 이상, 14일 유지 (신규 개인 계정 요건 — 출시 시점 정책 재확인)
5. 프로덕션 출시

전체 전략은 [`docs/PLAY_STRATEGY.md`](../docs/PLAY_STRATEGY.md).
