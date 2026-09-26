# song-english

Girl on Fire 7일 파일럿 — 유튜브 A-B 루퍼가 붙은 단일 페이지.

- 빌드 없음. `index.html` 하나가 전부.
- 입력(가사·카드·진행)은 **브라우저 localStorage 에만** 저장된다. 저장소에는 아무 개인 데이터도 올라가지 않는다.
- 정본은 이 저장소다. 워크스페이스에는 사본을 두지 않는다.

## Android (Google Play)

- PWA: `manifest.webmanifest` · `sw.js` · `icons/`
- TWA 패키징: [`android/`](android/README.md)
- 출시 전략: [`docs/PLAY_STRATEGY.md`](docs/PLAY_STRATEGY.md)
- 대기자 랜딩 페이지: [`landing/`](landing/README.md) · Supabase 스키마 [`supabase/waitlist.sql`](supabase/waitlist.sql)
