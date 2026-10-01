# Focus Probe — 교통 안내 출처 진단 앱

안드로이드 오토로 YouTube 를 듣는 중 끼어드는 교통 안내가 **폰에서 나오는지, 차량에서 나오는지** 판정한다.
폰에서 나와야 "안내 동안 일시정지 → 되감기 → 재개" 앱을 만들 수 있다. 이 앱은 그 Go / No-Go 판단용이다.

## 무엇을 기록하나

| 관찰 대상 | API | 알아내는 것 |
| :--- | :--- | :--- |
| 다른 앱의 재생 usage | `AudioManager.AudioPlaybackCallback` | 길안내 음성(`NAV_GUIDANCE`) 등 끼어드는 소리의 발생 여부, 길이 |
| usage 별 플레이어 수 | 위와 동일 | 안내를 `MEDIA` 채널로 내는 내비 앱 탐지 |
| 미디어 세션 상태 | `MediaSessionManager` (알림 접근 권한) | 안내 중 YouTube 가 **멈추는지 / 덕킹되는지**, 되감기(`seekTo`) 지원 여부 |
| 차량 모드 | `UiModeManager` | 안드로이드 오토 연결 구간 |
| 내비 앱 알림 | `NotificationListenerService` | 어떤 내비 앱이 살아 있는지 (패키지명만, **알림 내용은 저장 안 함**) |

- 오디오 포커스를 직접 요청하지 않는다. 요청하면 YouTube 가 멈춰 관찰이 오염된다.
- 로그는 앱 내부 저장소에만 쌓인다 (최대 약 2MB, 넘으면 오래된 절반 삭제). 외부 전송 없음.

## 설치

1. GitHub → **Actions** → `focus-probe APK` 최신 실행 → `focus-probe-debug-apk` 다운로드 → 압축 해제 → `app-debug.apk` 설치
   (출처를 알 수 없는 앱 설치 허용 필요)
2. 앱 실행 → **1. 알림 접근 허용하기** → Focus Probe 켜기
   - 스위치가 회색으로 막혀 있으면 (Android 13+ 사이드로드 제한):
     설정 → 앱 → Focus Probe → 우측 상단 ⋮ → **제한된 설정 허용** → 다시 시도
3. 화면 상단에 `● 알림 접근: 허용됨` 이 보이면 끝. 앱을 닫아도 백그라운드에서 기록된다.

> CI 빌드마다 디버그 서명 키가 달라 **업데이트 설치가 실패하면 기존 앱을 삭제 후 재설치**.

## 사용법

1. 평소처럼 안드로이드 오토 연결 → YouTube 팟캐스트 재생 → 운전
2. 교통 안내가 3~5회 이상 나올 때까지 주행 (운전 중 폰 조작 금지)
3. 주차 후 앱 열기 → 판정 확인 → 필요하면 **로그 공유**로 원본 전달

## 판정 해석

| 판정 | 의미 | 다음 단계 |
| :--- | :--- | :--- |
| **GO ✅** | `NAV_GUIDANCE` 감지됨 = 폰 앱이 안내 | 오케스트레이터 MVP 개발 |
| **조건부 GO ⚠️** (기타 usage) | 다른 채널로 끼어듦 (예: `NOTIFICATION`) | 해당 usage 를 감지 대상으로 설계 |
| **조건부 GO ⚠️** (MEDIA 버스트) | 안내가 미디어 채널로 나옴 | 플레이어 수 변화 감지, 정확도 검증 필요 |
| **NO-GO ❌** | 안내가 들렸는데 폰에서 잡힌 게 없음 | 차량 순정 내비 음성 설정에서 끄기 |

추가로 확인할 값:
- **일시정지 / 덕킹 비율**: 덕킹이면 지금은 소리만 작아지는 것 → 앱이 대신 멈춰 줘야 함
- **되감기 가능 여부 (YouTube)**: "불가"면 MVP 는 정지·재개만, 되감기 없이 설계
- **안내 평균·최대 길이**: 재개 대기시간 설계값

## 로그 포맷 (TSV)

`epoch_ms	local_time	event	subject	detail` — `interrupt_events` 테이블로 바로 적재 가능.

이벤트: `LISTENER`, `CAR_MODE`, `USAGE_ON`, `USAGE_OFF`, `PLAYERS`, `SESSION_ADD`, `SESSION_GONE`, `MEDIA_STATE`, `NAV_APP_NOTIF`

## 로컬 빌드

Android SDK 가 있는 환경에서 `./gradlew assembleDebug`. 외부 라이브러리 의존성 없음.
