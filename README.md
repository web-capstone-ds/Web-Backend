# DS Vision Web Backend

웹/모바일 프론트엔드에 장비 KPI, 검사 이력, AI 보고서를 제공하는 REST API 서버.  
JWT 인증, PostgreSQL 데이터 저장, AI 서버 연동을 담당합니다.

## 기술 스택

| 항목 | 내용 |
|---|---|
| Language | Java |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL 16 |
| Migration | Flyway |
| ORM | Spring Data JPA (Hibernate) |
| 인증 | JWT (HS256, Access 30분 / Refresh 7일) |
| 빌드 | Gradle |

## 주요 기능

- **인증**: JWT 기반 로그인, 토큰 갱신
- **장비 KPI**: 수율, 가동률, 불량 분포 등 집계 데이터 제공
- **검사 이력**: LOT별 INSPECTION_RESULT 조회
- **AI 연동**: AI 서버(`:8000`)에 질의 및 보고서 조회 프록시
- **관리자**: 초기 ADMIN 계정 자동 생성

## 실행 방법

```bash
cd Web-Backend

# 환경변수 설정
cp .env.example .env   # (또는 .env 직접 편집)
# PG_NAME, PG_USER, PG_PASSWORD, JWT_SECRET 등 필수값 입력

# Docker Compose로 서버 + DB 실행
docker compose up -d
```

로컬 개발 시:
```bash
docker compose up postgres -d    # DB만 먼저 실행
./gradlew bootRun
```

## 포트

| 서비스 | 포트 |
|---|---|
| Web Backend (Spring Boot) | 8080 |
| PostgreSQL | 5436 (호스트) / 5432 (컨테이너) |

## 주요 환경변수

| 변수 | 설명 |
|---|---|
| `PG_HOST` | DB 호스트 (Docker 내부: `postgres`) |
| `PG_NAME` | DB 이름 |
| `PG_USER` / `PG_PASSWORD` | DB 계정 |
| `JWT_SECRET` | JWT 서명 키 (64자 이상) |
| `AI_SERVER_ENABLED` | AI 서버 연동 활성화 (`true`/`false`) |
| `AI_SERVER_URL` | AI 서버 주소 |
| `APP_CORS_ALLOWED_ORIGINS` | CORS 허용 Origin 목록 |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | 초기 관리자 계정 |
