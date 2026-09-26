# 대기자 랜딩 페이지 (Phase 0 · 수요 검증)

`landing/index.html` 한 장. 빌드 없음. 영어 페이지이고, 가수 이름·실제 가사·아티스트 이미지는 쓰지 않는다 (예시 가사는 직접 지은 문장).

## 1. Supabase 연결 (10분)

1. [supabase.com](https://supabase.com) 에서 프로젝트 생성
2. **SQL Editor** 에 [`supabase/waitlist.sql`](../supabase/waitlist.sql) 을 붙여넣고 실행
3. **Project Settings → API Keys** 에서 publishable 키(`sb_publishable_…`) 또는 레거시 `anon` 키(`eyJ…`)를, **Data API** 에서 `Project URL` 을 복사 (둘 다 동작)
4. `landing/index.html` 의 두 줄을 채운다

```js
var SUPABASE_URL = "https://xxxx.supabase.co";
var SUPABASE_ANON_KEY = "eyJ...";
```

- anon 키는 페이지에 공개돼도 된다. RLS 가 **INSERT 만** 허용하므로 밖에서 명단을 읽을 수 없다.
- **`service_role` 키는 절대 넣지 않는다.** 넣으면 누구나 명단 전체를 읽고 지울 수 있다.
- 두 값이 비어 있으면 **데모 모드**: 전송하지 않고 브라우저 콘솔에 출력만 한 뒤 완료 화면을 보여준다.

## 2. 배포

GitHub Pages 를 켜면 `https://<도메인>/landing/` 으로 열린다. 커스텀 도메인을 산다면 랜딩을 도메인 루트에 두는 것이 숏폼 링크로 더 좋다.

## 3. 유입 경로 추적

링크 끝에 `?src=` 를 붙이면 `waitlist.src` 에 기록된다 (`utm_source` 도 인식).

| 채널 | 링크 |
| :-- | :-- |
| TikTok 프로필 | `.../landing/?src=tiktok` |
| Instagram Reels | `.../landing/?src=reels` |
| 커뮤니티 글 | `.../landing/?src=community` |
| 가입자 공유 버튼 | 자동으로 `?src=share` |

## 4. 판단 (7일 후)

Supabase **SQL Editor** 에서:

```sql
select * from waitlist_by_country;
```

| 기준 | 판단 |
| :-- | :-- |
| 전체 가입 < 100 | 방향 재검토 — 개발 착수 보류 |
| 대상 국가 `median_price_usd` ≥ 2.99 | 선결제 유료 앱 |
| 대상 국가 `median_price_usd` < 2.99 | 무료 + 1회 해금 |

`top_bias_group` 과 `song_request` 는 첫 루틴 템플릿과 숏폼 소재를 고르는 데 쓴다.

## 5. 개인정보

이메일을 모으므로 공개 전에 간단한 개인정보 처리방침 페이지를 두는 것을 권장한다 (수집 항목 · 목적: 출시 알림 1회 · 보관 기간 · 삭제 요청 방법).
