CREATE DATABASE IF NOT EXISTS fimobook
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE fimobook;

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
    INDEX idx_reviews_cid_created (cid, created_at),
    INDEX idx_reviews_user_id (user_id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_player FOREIGN KEY (cid) REFERENCES players (cid) ON DELETE CASCADE,
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
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
