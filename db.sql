CREATE DATABASE orda;

CREATE EXTENSION IF NOT EXISTS postgis;

-- ──────────────────────────────────────────────
-- trail_nodes
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

-- ──────────────────────────────────────────────
-- users
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
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_phone ON users (phone);

-- ──────────────────────────────────────────────
-- hiking_sessions
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
CREATE INDEX idx_hiking_sessions_user_id ON hiking_sessions (user_id);
CREATE INDEX idx_hiking_sessions_status ON hiking_sessions (status);
CREATE INDEX idx_hiking_sessions_started_at ON hiking_sessions (started_at DESC);

-- ──────────────────────────────────────────────
-- gps_tracks
-- ──────────────────────────────────────────────
CREATE TABLE gps_tracks
(
    track_id              BIGSERIAL PRIMARY KEY,
    session_id            BIGINT    NOT NULL REFERENCES hiking_sessions (session_id) ON DELETE CASCADE,
    sequence_num          INTEGER   NOT NULL,
    raw_latitude          DOUBLE PRECISION NOT NULL,
    raw_longitude         DOUBLE PRECISION NOT NULL,
    raw_elevation_m       DOUBLE PRECISION,
    snapped_latitude      DOUBLE PRECISION,
    snapped_longitude     DOUBLE PRECISION,
    canonical_elevation_m DOUBLE PRECISION,
    elevation_source      VARCHAR(20) NOT NULL DEFAULT 'none'
        CHECK (elevation_source IN ('dem', 'gps_fallback', 'none')),
    accuracy_m            DOUBLE PRECISION,
    recorded_at           TIMESTAMP NOT NULL DEFAULT now(),
    geom                  GEOMETRY(Point, 4326) NOT NULL,

    UNIQUE (session_id, sequence_num)
);
CREATE INDEX idx_gps_tracks_session_id ON gps_tracks (session_id, sequence_num);
CREATE INDEX idx_gps_tracks_geom ON gps_tracks USING GIST (geom);

-- ──────────────────────────────────────────────
-- summit_verifications
-- verification_method: gps(MVP) / photo(도전과제) / photo_exif / sign_recognition
-- photo_path: 사진 인증 시 저장된 사진 경로 (GPS 인증은 null)
-- ──────────────────────────────────────────────
CREATE TABLE summit_verifications
(
    verification_id      BIGSERIAL PRIMARY KEY,
    session_id           BIGINT           NOT NULL REFERENCES hiking_sessions (session_id) ON DELETE CASCADE,
    summit_id            TEXT             NOT NULL REFERENCES summit_points (summit_id),
    distance_to_summit_m DOUBLE PRECISION NOT NULL,
    verification_method  VARCHAR(30)      NOT NULL DEFAULT 'gps'
        CHECK (verification_method IN ('gps', 'photo', 'photo_exif', 'sign_recognition')),
    photo_path           VARCHAR(500),
    verified_at          TIMESTAMP        NOT NULL DEFAULT now(),
    geom                 GEOMETRY(Point, 4326) NOT NULL,

    UNIQUE (session_id, summit_id)
);
CREATE INDEX idx_summit_verifications_session ON summit_verifications (session_id);
CREATE INDEX idx_summit_verifications_summit ON summit_verifications (summit_id);

-- ──────────────────────────────────────────────
-- user_stats
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
CREATE INDEX idx_user_stats_user_id ON user_stats (user_id);