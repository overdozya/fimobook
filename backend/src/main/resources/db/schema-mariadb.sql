CREATE DATABASE IF NOT EXISTS fimobook
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE fimobook;

CREATE TABLE IF NOT EXISTS player_profiles (
    pid                 BIGINT       NOT NULL COMMENT '실제 선수 고유 ID',
    player_name_kor     VARCHAR(100) NOT NULL,
    player_name_eng     VARCHAR(150) NULL,
    nationality_id      BIGINT       NULL,
    nation_name         VARCHAR(100) NULL,
    birth_date          DATE         NULL,
    height_cm           SMALLINT     NULL,
    weight_kg           SMALLINT     NULL,
    main_foot           TINYINT      NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (pid),
    INDEX idx_player_profiles_name_kor (player_name_kor)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_classes (
    class_id            VARCHAR(100) NOT NULL,
    class_name          VARCHAR(150) NOT NULL,
    image_url           VARCHAR(500) NULL,
    source_updated_at   DATETIME     NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (class_id),
    INDEX idx_player_classes_name (class_name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nations (
    nation_id           BIGINT       NOT NULL,
    name_kor            VARCHAR(100) NOT NULL,
    name_eng            VARCHAR(100) NULL,
    is_visible          BOOLEAN      NOT NULL DEFAULT TRUE,
    flag_url            VARCHAR(500) NULL,
    PRIMARY KEY (nation_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS leagues (
    league_id           BIGINT       NOT NULL,
    name_kor            VARCHAR(150) NOT NULL,
    name_eng            VARCHAR(150) NULL,
    country_id          BIGINT       NULL,
    level               SMALLINT     NULL,
    logo_url            VARCHAR(500) NULL,
    PRIMARY KEY (league_id),
    INDEX idx_leagues_country (country_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS teams (
    team_id             BIGINT       NOT NULL,
    team_name           VARCHAR(150) NOT NULL,
    logo_url            VARCHAR(500) NULL,
    PRIMARY KEY (team_id),
    INDEX idx_teams_name (team_name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS players (
    cid                 BIGINT       NOT NULL COMMENT '카드 고유 ID',
    pid                 BIGINT       NOT NULL COMMENT '실제 선수 고유 ID',
    player_name_kor     VARCHAR(100) NOT NULL,
    player_name_eng     VARCHAR(150) NULL,
    player_image_url    VARCHAR(500) NULL,
    background_image_url VARCHAR(500) NULL,
    overall_rating      SMALLINT     NOT NULL,
    primary_position    VARCHAR(10)  NOT NULL,
    potential_position VARCHAR(100)  NULL,
    team_id             BIGINT       NULL,
    team_name           VARCHAR(150) NULL,
    league_id           BIGINT       NULL,
    league_name         VARCHAR(150) NULL,
    nationality_id      BIGINT       NULL,
    nation_name         VARCHAR(100) NULL,
    height_cm           SMALLINT     NULL,
    weight_kg           SMALLINT     NULL,
    main_foot           TINYINT      NULL COMMENT '1: 오른발, 2: 왼발',
    weak_foot_rating    TINYINT      NULL,
    skill_moves_level   TINYINT      NULL,
    skill_moves_name    VARCHAR(100) NULL,
    player_year         SMALLINT     NULL,

    stats_data          JSON         NULL COMMENT '현재 상세 화면의 ACC, FIN 등 능력치',
    prices_data         JSON         NULL COMMENT '현재 상세 화면의 n8Price0~15',
    traits_data         JSON         NULL COMMENT '현재 상세 화면의 Trait 배열',
    raw_data            JSON         NULL COMMENT '아직 컬럼화하지 않은 원본 필드 보존',

    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (cid),
    INDEX idx_players_pid (pid),
    INDEX idx_players_name_kor (player_name_kor),
    INDEX idx_players_position (primary_position),
    INDEX idx_players_team_id (team_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

ALTER TABLE players
    ADD COLUMN IF NOT EXISTS is_tradeable BOOLEAN NOT NULL DEFAULT TRUE AFTER player_year,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER is_tradeable,
    ADD COLUMN IF NOT EXISTS positions_data JSON NULL AFTER traits_data,
    ADD COLUMN IF NOT EXISTS play_styles_data JSON NULL AFTER positions_data,
    ADD COLUMN IF NOT EXISTS skills_data JSON NULL AFTER play_styles_data,
    ADD COLUMN IF NOT EXISTS price_checked_at DATETIME NULL AFTER raw_data,
    ADD COLUMN IF NOT EXISTS source_seen_at DATETIME NULL AFTER price_checked_at,
    ADD INDEX IF NOT EXISTS idx_players_league_id (league_id),
    ADD INDEX IF NOT EXISTS idx_players_nationality_id (nationality_id),
    ADD INDEX IF NOT EXISTS idx_players_ovr (overall_rating),
    ADD INDEX IF NOT EXISTS idx_players_active_name (is_active, player_name_kor),
    ADD INDEX IF NOT EXISTS idx_players_catalog_rank
        (is_active, is_tradeable, overall_rating, cid);

-- Older development revisions briefly stored one inferred class on players.
-- Official class filters overlap, so card_classes is the only source of truth.
ALTER TABLE players
    DROP INDEX IF EXISTS idx_players_class_id,
    DROP COLUMN IF EXISTS class_image_url,
    DROP COLUMN IF EXISTS class_name,
    DROP COLUMN IF EXISTS class_id;

CREATE TABLE IF NOT EXISTS card_classes (
    cid                 BIGINT       NOT NULL,
    class_id            VARCHAR(100) NOT NULL,
    PRIMARY KEY (cid, class_id),
    INDEX idx_card_classes_filter (class_id, cid),
    CONSTRAINT fk_card_classes_player FOREIGN KEY (cid) REFERENCES players (cid) ON DELETE CASCADE,
    CONSTRAINT fk_card_classes_class FOREIGN KEY (class_id) REFERENCES player_classes (class_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS card_positions (
    cid                 BIGINT      NOT NULL,
    position_code       VARCHAR(10) NOT NULL,
    position_kind       VARCHAR(20) NOT NULL COMMENT 'PRIMARY, POTENTIAL, SECONDARY, TERTIARY',
    penalty             SMALLINT    NULL,
    sort_order          SMALLINT    NOT NULL DEFAULT 0,
    PRIMARY KEY (cid, position_kind, position_code),
    INDEX idx_card_positions_filter (position_code, position_kind),
    CONSTRAINT fk_card_positions_player FOREIGN KEY (cid) REFERENCES players (cid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS traits (
    trait_id            BIGINT       NOT NULL,
    trait_name          VARCHAR(100) NOT NULL,
    asset_key           VARCHAR(100) NULL,
    icon_url            VARCHAR(500) NULL,
    PRIMARY KEY (trait_id),
    INDEX idx_traits_name (trait_name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS card_traits (
    cid                 BIGINT NOT NULL,
    trait_id            BIGINT NOT NULL,
    PRIMARY KEY (cid, trait_id),
    INDEX idx_card_traits_trait (trait_id, cid),
    CONSTRAINT fk_card_traits_player FOREIGN KEY (cid) REFERENCES players (cid) ON DELETE CASCADE,
    CONSTRAINT fk_card_traits_trait FOREIGN KEY (trait_id) REFERENCES traits (trait_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS play_styles (
    play_style_id       VARCHAR(100) NOT NULL,
    play_style_name     VARCHAR(100) NULL,
    description         VARCHAR(500) NULL,
    icon_url            VARCHAR(500) NULL,
    PRIMARY KEY (play_style_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS card_play_styles (
    cid                 BIGINT       NOT NULL,
    play_style_id       VARCHAR(100) NOT NULL,
    slot_order          SMALLINT     NOT NULL DEFAULT 0,
    is_original         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (cid, play_style_id),
    INDEX idx_card_play_styles_style (play_style_id, cid),
    CONSTRAINT fk_card_play_styles_player FOREIGN KEY (cid) REFERENCES players (cid) ON DELETE CASCADE,
    CONSTRAINT fk_card_play_styles_style FOREIGN KEY (play_style_id) REFERENCES play_styles (play_style_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS skills (
    skill_id            VARCHAR(100) NOT NULL,
    skill_name          VARCHAR(100) NULL,
    icon_url            VARCHAR(500) NULL,
    PRIMARY KEY (skill_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS card_skills (
    cid                 BIGINT       NOT NULL,
    skill_id            VARCHAR(100) NOT NULL,
    skill_level         SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (cid, skill_id),
    INDEX idx_card_skills_skill (skill_id, cid),
    CONSTRAINT fk_card_skills_player FOREIGN KEY (cid) REFERENCES players (cid) ON DELETE CASCADE,
    CONSTRAINT fk_card_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (skill_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS card_prices_current (
    cid                 BIGINT      NOT NULL,
    enhancement_level   TINYINT     NOT NULL,
    price               BIGINT      NOT NULL DEFAULT 0,
    observed_at         DATETIME    NOT NULL,
    changed_at          DATETIME    NOT NULL,
    PRIMARY KEY (cid, enhancement_level),
    INDEX idx_card_prices_filter (enhancement_level, price, cid),
    CONSTRAINT fk_card_prices_current_player FOREIGN KEY (cid) REFERENCES players (cid) ON DELETE CASCADE,
    CONSTRAINT chk_card_prices_current_level CHECK (enhancement_level BETWEEN 0 AND 15)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS card_price_history (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    cid                 BIGINT      NOT NULL,
    enhancement_level   TINYINT     NOT NULL,
    price               BIGINT      NOT NULL,
    observed_at         DATETIME    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_card_price_history_card (cid, enhancement_level, observed_at),
    CONSTRAINT fk_card_price_history_player FOREIGN KEY (cid) REFERENCES players (cid) ON DELETE CASCADE,
    CONSTRAINT chk_card_price_history_level CHECK (enhancement_level BETWEEN 0 AND 15)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS data_sync_runs (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    sync_type           VARCHAR(30)  NOT NULL,
    source_id           VARCHAR(100) NULL,
    status              VARCHAR(20)  NOT NULL,
    reported_count      INT          NULL,
    processed_count     INT          NOT NULL DEFAULT 0,
    inserted_count      INT          NOT NULL DEFAULT 0,
    updated_count       INT          NOT NULL DEFAULT 0,
    error_message       VARCHAR(1000) NULL,
    started_at          DATETIME     NOT NULL,
    completed_at        DATETIME     NULL,
    PRIMARY KEY (id),
    INDEX idx_data_sync_runs_started (sync_type, started_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS price_refresh_jobs (
    pid                 BIGINT       NOT NULL COMMENT '동일 실제 선수의 중복 작업 제거 키',
    requested_cid       BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts            SMALLINT     NOT NULL DEFAULT 0,
    available_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at           DATETIME     NULL,
    last_error          VARCHAR(1000) NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (pid),
    INDEX idx_price_refresh_jobs_poll (status, available_at),
    CONSTRAINT fk_price_refresh_jobs_player FOREIGN KEY (requested_cid) REFERENCES players (cid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reviews (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    cid        BIGINT       NOT NULL,
    rating     TINYINT      NOT NULL,
    content    VARCHAR(100) NOT NULL,
    likes      INT          NOT NULL DEFAULT 0,
    dislikes   INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_user_card (user_id, cid),
    INDEX idx_reviews_cid_created (cid, created_at),
    INDEX idx_reviews_user_id (user_id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_player FOREIGN KEY (cid) REFERENCES players (cid) ON DELETE CASCADE,
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    expires_at  DATETIME    NOT NULL,
    revoked_at  DATETIME    NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_refresh_token_hash (token_hash),
    INDEX idx_auth_refresh_tokens_user (user_id),
    INDEX idx_auth_refresh_tokens_expiry (expires_at),
    CONSTRAINT fk_auth_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS review_reactions (
    review_id   BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    reaction    VARCHAR(10) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (review_id, user_id),
    INDEX idx_review_reactions_user (user_id),
    CONSTRAINT fk_review_reactions_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reactions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_review_reactions_type CHECK (reaction IN ('like', 'dislike'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS squads (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL DEFAULT '내 스쿼드',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_squads_user_name (user_id, name),
    CONSTRAINT fk_squads_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS squad_players (
    squad_id BIGINT      NOT NULL,
    slot_id  VARCHAR(10) NOT NULL,
    cid      BIGINT      NOT NULL,
    PRIMARY KEY (squad_id, slot_id),
    UNIQUE KEY uk_squad_card (squad_id, cid),
    CONSTRAINT fk_squad_players_squad FOREIGN KEY (squad_id) REFERENCES squads (id) ON DELETE CASCADE,
    CONSTRAINT fk_squad_players_player FOREIGN KEY (cid) REFERENCES players (cid) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
