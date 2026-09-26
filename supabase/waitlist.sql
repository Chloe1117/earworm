-- Hookline 대기자 테이블 — Supabase SQL Editor 에 그대로 붙여넣어 실행한다.
-- 랜딩 페이지는 공개 anon 키로 INSERT 만 한다. 읽기·수정·삭제는 막는다.

create table if not exists public.waitlist (
  id           uuid primary key default gen_random_uuid(),
  email        text not null check (char_length(email) between 5 and 254 and email like '%@%'),
  country      text not null check (char_length(country) between 2 and 2),   -- ISO 3166-1 alpha-2, 'ZZ' = 기타
  bias_group   text check (char_length(bias_group) <= 80),                  -- 최애 그룹 (팬덤 용어)
  song_request text check (char_length(song_request) <= 120),               -- 처음 배우고 싶은 곡
  price_ok_usd numeric check (price_ok_usd between 0 and 20),               -- 1회 결제로 낼 수 있는 최고 가격
  src          text check (char_length(src) <= 40),                         -- tiktok | reels | community | share | direct
  lang         text check (char_length(lang) <= 20),                        -- 브라우저 언어
  consent      boolean not null check (consent),                            -- 출시 알림 이메일 수신 동의
  created_at   timestamptz not null default now()
);

-- 같은 이메일 중복 가입 방지 (대소문자 무시). 중복이면 API 가 409 를 돌려준다.
create unique index if not exists waitlist_email_uniq on public.waitlist (lower(email));

alter table public.waitlist enable row level security;

-- 익명 방문자는 INSERT 만. SELECT 정책이 없으므로 명단은 밖에서 읽을 수 없다.
drop policy if exists "anon can join waitlist" on public.waitlist;
create policy "anon can join waitlist"
  on public.waitlist for insert
  to anon
  with check (true);

revoke all on public.waitlist from anon;
grant insert on public.waitlist to anon;

-- 판단용 뷰 — 대시보드(서비스 키)에서만 조회한다.
-- 4장 가격 규칙: 대상 국가 중앙값 ≥ 2.99 → 선결제 유료, 미만 → 무료 + 1회 해금
create or replace view public.waitlist_by_country
with (security_invoker = true) as
select
  country,
  count(*)                                                        as signups,
  percentile_cont(0.5) within group (order by price_ok_usd)       as median_price_usd,
  round(avg((price_ok_usd >= 2.99)::int) * 100, 1)                as pct_pay_299_plus,
  mode() within group (order by bias_group)                       as top_bias_group
from public.waitlist
group by country
order by signups desc;

revoke all on public.waitlist_by_country from anon, authenticated;
