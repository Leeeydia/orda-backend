CREATE DATABASE orda;
-- ──────────────────────────────────────────────
-- ORDA C단계 PostGIS 적재 스키마
-- 좌표계: WGS84 / EPSG:4326
-- geometry: 2D (고도는 속성 컬럼으로 분리)
-- ──────────────────────────────────────────────
-- 확장 활성화
CREATE EXTENSION IF NOT EXISTS postgis;
-- ──────────────────────────────────────────────
-- trail_nodes
-- 원본: node_with_elevation.geojson
-- ──────────────────────────────────────────────
CREATE TABLE trail_nodes
(
    node_id          TEXT PRIMARY KEY,
    node_type        TEXT,
    degree           INTEGER,
    elevation_m      DOUBLE PRECISION,
    elevation_status TEXT,
    qa_status        TEXT,
    geom             GEOMETRY(Point, 4326)
);
CREATE INDEX idx_trail_nodes_geom ON trail_nodes USING GIST (geom);
-- ──────────────────────────────────────────────
-- trail_edges
-- 원본: final_trail_dataset.geojson
--
-- elevation_diff_m: 현재는 end-start 단순 차이값.
--   진짜 누적 상승고도(gain/loss)는 edge 내부 샘플링 구현 후 컬럼 추가 예정.
-- ──────────────────────────────────────────────
CREATE TABLE trail_edges
(
    edge_id           TEXT PRIMARY KEY,
    start_node_id     TEXT NOT NULL REFERENCES trail_nodes (node_id),
    end_node_id       TEXT NOT NULL REFERENCES trail_nodes (node_id),
    distance_m        DOUBLE PRECISION,
    elevation_start_m DOUBLE PRECISION,
    elevation_end_m   DOUBLE PRECISION,
    elevation_diff_m  DOUBLE PRECISION,
    slope_percent     DOUBLE PRECISION,
    difficulty_score  DOUBLE PRECISION,
    difficulty        TEXT,
    surface           TEXT,
    nearest_summit_id TEXT,
    qa_status         TEXT,
    geom              GEOMETRY(LineString, 4326)
);
CREATE INDEX idx_trail_edges_geom ON trail_edges USING GIST (geom);
-- ──────────────────────────────────────────────
-- summit_points
-- 원본: summit_points.geojson
-- ──────────────────────────────────────────────
CREATE TABLE summit_points
(
    summit_id   TEXT PRIMARY KEY,
    name        TEXT,
    elevation_m DOUBLE PRECISION,
    source      TEXT,
    radius_m    DOUBLE PRECISION,
    geom        GEOMETRY(Point, 4326)
);
CREATE INDEX idx_summit_points_geom ON summit_points USING GIST (geom);


-- ============================================================
-- ============================================================
--
--  ORDA MVP 추가 테이블
--  기능정의서(ORDA_MVP.xlsx) 기반
--  아래 테이블은 기존 C단계 스키마 위에 추가됨
--
-- ============================================================
-- ============================================================


-- ──────────────────────────────────────────────
-- users
-- 기능정의서 No.1 회원가입, No.2 로그인/로그아웃
-- 담당: A - 이윤지
-- ──────────────────────────────────────────────
CREATE TABLE users
(
    user_id           BIGSERIAL    PRIMARY KEY,
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    nickname          VARCHAR(50)  NOT NULL,
    name              VARCHAR(50)  NOT NULL,
    phone             VARCHAR(20)  NOT NULL UNIQUE,
    birth_date        DATE,
    profile_image_url VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

COMMENT ON TABLE  users IS '사용자 계정 (기능정의서 No.1 회원가입, No.2 로그인/로그아웃)';
COMMENT ON COLUMN users.email IS '로그인 식별자, 이메일 기반 회원가입';
COMMENT ON COLUMN users.password_hash IS 'BCrypt 해시 처리된 비밀번호';
COMMENT ON COLUMN users.nickname IS '서비스 내 표시 이름';
COMMENT ON COLUMN users.name IS '실명 (필수)';
COMMENT ON COLUMN users.phone IS '전화번호 (필수, 010-XXXX-XXXX)';
COMMENT ON COLUMN users.birth_date IS '생년월일 (선택)';

-- 도전과제: 카카오 소셜 로그인 추가 시
-- ALTER TABLE users ADD COLUMN kakao_id VARCHAR(50) UNIQUE;
-- ALTER TABLE users ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'local'; -- local / kakao

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_phone ON users (phone);


-- ──────────────────────────────────────────────
-- hiking_sessions
-- 기능정의서 No.3 등산 시작/종료, No.4 경로 저장의 상위 단위
-- 3D 리플레이(No.11, No.12)도 이 데이터를 활용
-- 담당: A - 이윤지
-- ──────────────────────────────────────────────
CREATE TABLE hiking_sessions
(
    session_id             BIGSERIAL PRIMARY KEY,
    user_id                BIGINT      NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    status                 VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'ABANDONED')),
    started_at             TIMESTAMP   NOT NULL DEFAULT now(),
    ended_at               TIMESTAMP,
    total_distance_m       DOUBLE PRECISION,
    total_elevation_gain_m DOUBLE PRECISION,
    total_elevation_loss_m DOUBLE PRECISION,
    total_duration_sec     INTEGER,
    start_point            GEOMETRY(Point, 4326),
    end_point              GEOMETRY(Point, 4326),
    created_at             TIMESTAMP   NOT NULL DEFAULT now()
);

COMMENT ON TABLE  hiking_sessions IS '등산 세션 (기능정의서 No.3 등산 시작/종료, No.4 경로 저장)';
COMMENT ON COLUMN hiking_sessions.status IS 'ACTIVE: 진행중, PAUSED: 일시정지, COMPLETED: 완료, ABANDONED: 포기';
COMMENT ON COLUMN hiking_sessions.total_distance_m IS '총 이동 거리 (미터)';
COMMENT ON COLUMN hiking_sessions.total_elevation_gain_m IS '총 누적 상승 고도 (미터)';
COMMENT ON COLUMN hiking_sessions.total_elevation_loss_m IS '총 누적 하강 고도 (미터)';

CREATE INDEX idx_hiking_sessions_user_id ON hiking_sessions (user_id);
CREATE INDEX idx_hiking_sessions_status ON hiking_sessions (status);
CREATE INDEX idx_hiking_sessions_started_at ON hiking_sessions (started_at DESC);


-- ──────────────────────────────────────────────
-- gps_tracks
-- 기능정의서 No.4 실시간 GPS 수집
-- 등산 중 위치 좌표를 수집하여 경로를 저장
-- 3D 리플레이(No.11 경로 재생, No.12 고도 변화 시각화)의 원본 데이터
-- 담당: A - 이윤지
--
-- geom: snapped 좌표 기준 (등산로에 스냅된 공식 위치)
-- canonical_elevation_m: DEM 기준 공식 고도 (프로파일/상승고도 계산에 사용)
-- raw_*: 기기 GPS 원본값 (참고용)
-- ──────────────────────────────────────────────
CREATE TABLE gps_tracks
(
    track_id              BIGSERIAL PRIMARY KEY,
    session_id            BIGINT    NOT NULL REFERENCES hiking_sessions (session_id) ON DELETE CASCADE,
    sequence_num          INTEGER   NOT NULL,

    -- raw GPS 원본 (참고용, 공식값으로 사용하지 않음)
    raw_latitude          DOUBLE PRECISION NOT NULL,
    raw_longitude         DOUBLE PRECISION NOT NULL,
    raw_elevation_m       DOUBLE PRECISION,

    -- canonical 기준 (공식값)
    snapped_latitude      DOUBLE PRECISION,
    snapped_longitude     DOUBLE PRECISION,
    canonical_elevation_m DOUBLE PRECISION,
    elevation_source      VARCHAR(20) NOT NULL DEFAULT 'none'
        CHECK (elevation_source IN ('dem', 'gps_fallback', 'none')),

    accuracy_m            DOUBLE PRECISION,
    recorded_at           TIMESTAMP NOT NULL DEFAULT now(),

    -- geom: snapped 좌표 기준으로 저장 (지도 표시 / 공간 연산 기준)
    geom                  GEOMETRY(Point, 4326) NOT NULL,

    UNIQUE (session_id, sequence_num)
);

COMMENT ON TABLE  gps_tracks IS 'GPS 트랙 포인트 (기능정의서 No.4 실시간 GPS 수집)';
COMMENT ON COLUMN gps_tracks.sequence_num IS '세션 내 포인트 순서 (1부터 시작)';
COMMENT ON COLUMN gps_tracks.raw_latitude IS '기기 GPS 원본 위도 (참고용)';
COMMENT ON COLUMN gps_tracks.raw_longitude IS '기기 GPS 원본 경도 (참고용)';
COMMENT ON COLUMN gps_tracks.raw_elevation_m IS '기기 GPS 원본 고도 (참고용, null 가능)';
COMMENT ON COLUMN gps_tracks.snapped_latitude IS '등산로 스냅 위도 (공식 위치)';
COMMENT ON COLUMN gps_tracks.snapped_longitude IS '등산로 스냅 경도 (공식 위치)';
COMMENT ON COLUMN gps_tracks.canonical_elevation_m IS 'DEM 기준 공식 고도 - 프로파일/상승고도 계산 기준';
COMMENT ON COLUMN gps_tracks.elevation_source IS 'dem: DEM 샘플링 성공 / gps_fallback: GPS 원고도 대체 / none: 고도 없음';
COMMENT ON COLUMN gps_tracks.geom IS 'snapped 좌표 기준 (EPSG:4326), [lng, lat] 순서';
COMMENT ON COLUMN gps_tracks.accuracy_m IS 'GPS 정확도 (미터) - 수평 오차 반경';

CREATE INDEX idx_gps_tracks_session_id ON gps_tracks (session_id, sequence_num);
CREATE INDEX idx_gps_tracks_geom ON gps_tracks USING GIST (geom);


-- ──────────────────────────────────────────────
-- summit_verifications
-- 기능정의서 No.5 정상 도달 판별
-- 사용자 위치와 정상 좌표를 비교하여 정상 인증 처리
-- 마이페이지 기록 조회(No.13)에서도 사용
-- 담당: A - 이윤지
-- ──────────────────────────────────────────────
CREATE TABLE summit_verifications
(
    verification_id      BIGSERIAL PRIMARY KEY,
    session_id           BIGINT           NOT NULL REFERENCES hiking_sessions (session_id) ON DELETE CASCADE,
    summit_id            TEXT             NOT NULL REFERENCES summit_points (summit_id),
    distance_to_summit_m DOUBLE PRECISION NOT NULL,
    verification_method  VARCHAR(30)      NOT NULL DEFAULT 'gps'
        CHECK (verification_method IN ('gps', 'photo_exif', 'sign_recognition')),
    verified_at          TIMESTAMP        NOT NULL DEFAULT now(),
    geom                 GEOMETRY(Point, 4326) NOT NULL,

    UNIQUE (session_id, summit_id)
);

COMMENT ON TABLE  summit_verifications IS '정상 인증 기록 (기능정의서 No.5 정상 도달 판별)';
COMMENT ON COLUMN summit_verifications.distance_to_summit_m IS '인증 시점 사용자↔정상 간 거리 (미터)';
COMMENT ON COLUMN summit_verifications.verification_method IS 'gps: GPS 좌표 비교 (MVP), photo_exif/sign_recognition: 도전과제';
COMMENT ON COLUMN summit_verifications.geom IS '인증 시점의 사용자 GPS 좌표';

CREATE INDEX idx_summit_verifications_session ON summit_verifications (session_id);
CREATE INDEX idx_summit_verifications_summit ON summit_verifications (summit_id);


-- ──────────────────────────────────────────────
-- user_stats
-- 기능정의서 No.13 내 기록 조회, No.14 프로필/통계 조회
-- hiking_sessions, summit_verifications로부터 집계하는 캐싱 테이블
-- 담당: C - 윤종민
-- ──────────────────────────────────────────────
CREATE TABLE user_stats
(
    stat_id                BIGSERIAL PRIMARY KEY,
    user_id                BIGINT           NOT NULL UNIQUE REFERENCES users (user_id) ON DELETE CASCADE,
    total_hikes            INTEGER          NOT NULL DEFAULT 0,
    total_summits          INTEGER          NOT NULL DEFAULT 0,
    total_distance_m       DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_elevation_gain_m DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_duration_sec     INTEGER          NOT NULL DEFAULT 0,
    last_hiked_at          TIMESTAMP,
    updated_at             TIMESTAMP        NOT NULL DEFAULT now()
);

COMMENT ON TABLE  user_stats IS '사용자 통계 캐시 (기능정의서 No.13 내 기록 조회, No.14 프로필/통계 조회)';
COMMENT ON COLUMN user_stats.total_hikes IS '완료된 등산 횟수';
COMMENT ON COLUMN user_stats.total_summits IS '정상 인증 성공 횟수';

CREATE INDEX idx_user_stats_user_id ON user_stats (user_id);


-- ============================================================
-- 기능정의서 → 테이블 매핑
-- ============================================================
-- No.1  회원가입            → users
-- No.2  로그인/로그아웃      → users (Spring Security + JWT)
-- No.3  등산 시작/종료       → hiking_sessions
-- No.4  실시간 GPS 수집      → gps_tracks
-- No.5  정상 도달 판별       → summit_verifications + summit_points
-- No.6  프로젝트 기본 구조    → (코드 구조, DB 해당 없음)
-- No.7  환경 변수 관리       → (설정 파일, DB 해당 없음)
-- No.8  공통 API 응답 구조   → (코드 구조, DB 해당 없음)
-- No.9  에러 처리 및 로깅    → (코드 구조, DB 해당 없음)
-- No.10 지도 라이브러리 설정  → (Mapbox GL JS, DB 해당 없음)
-- No.11 3D 등산 리플레이     → hiking_sessions + gps_tracks 재사용
-- No.12 고도 변화 시각화     → gps_tracks.canonical_elevation_m 활용
-- No.13 내 기록 조회         → hiking_sessions + summit_verifications JOIN
-- No.14 프로필/통계 조회     → user_stats
-- No.15 Trail Difficulty    → trail_edges.difficulty
-- No.16 난이도 색상 지도     → trail_edges + trail_nodes
-- ============================================================

-- ============================================================
-- 도전과제 (MVP 이후 확장)
-- ============================================================
-- No.1 사진 EXIF 검증       → summit_verifications.verification_method = 'photo_exif'
-- No.2 정상 표지판 인식      → summit_verifications.verification_method = 'sign_recognition'
-- No.3 개인 업적 시스템      → achievements 테이블 (추후 추가)
-- No.4 Mountain Badge       → mountain_badges 테이블 (추후 추가)
-- No.5 리더보드             → leaderboard 테이블 (추후 추가)
-- No.6 다음 산 추천         → 추천 알고리즘 (trail_edges + user_stats 기반)
-- No.7 산/코스 리뷰         → reviews 테이블 (추후 추가)
-- No.8 카카오 소셜 로그인    → users.kakao_id, users.provider 컬럼 추가 예정
-- ============================================================