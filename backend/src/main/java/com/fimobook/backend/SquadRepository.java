package com.fimobook.backend;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class SquadRepository {

    public record SquadPlayer(String slotId, long cid, long pid, String playerKor, int ovr,
            String position, String pimage, long n8Price0) {
    }

    private final JdbcClient jdbcClient;

    public SquadRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<SquadPlayer> findDefault(long userId) {
        return jdbcClient.sql("""
                SELECT sp.slot_id, p.cid, p.pid, p.player_name_kor, p.overall_rating,
                       p.primary_position, p.player_image_url,
                       COALESCE(JSON_VALUE(p.prices_data, '$.n8Price0'), 0) AS base_price
                  FROM squads s
                  JOIN squad_players sp ON sp.squad_id = s.id
                  JOIN players p ON p.cid = sp.cid
                 WHERE s.user_id = :userId AND s.name = '내 스쿼드'
                """).param("userId", userId)
                .query((rs, rowNum) -> new SquadPlayer(rs.getString("slot_id"), rs.getLong("cid"),
                        rs.getLong("pid"), rs.getString("player_name_kor"), rs.getInt("overall_rating"),
                        rs.getString("primary_position"), rs.getString("player_image_url"),
                        rs.getLong("base_price")))
                .list();
    }

    @Transactional
    public List<SquadPlayer> saveDefault(long userId, List<SquadController.SquadSlotRequest> slots) {
        if (slots.size() > 11 || slots.stream().map(SquadController.SquadSlotRequest::slotId).distinct().count() != slots.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스쿼드 슬롯이 올바르지 않습니다.");
        }
        if (!slots.isEmpty()) {
            List<Long> cids = slots.stream().map(SquadController.SquadSlotRequest::cid).toList();
            String placeholders = String.join(",", java.util.Collections.nCopies(cids.size(), "?"));
            List<Long> pids = jdbcClient.sql("SELECT pid FROM players WHERE cid IN (" + placeholders + ")")
                    .params(cids).query(Long.class).list();
            if (pids.size() != cids.size() || pids.stream().distinct().count() != pids.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않거나 같은 선수가 중복되었습니다.");
            }
        }

        jdbcClient.sql("INSERT IGNORE INTO squads (user_id, name) VALUES (:userId, '내 스쿼드')")
                .param("userId", userId).update();
        long squadId = jdbcClient.sql("SELECT id FROM squads WHERE user_id=:userId AND name='내 스쿼드'")
                .param("userId", userId).query(Long.class).single();
        jdbcClient.sql("DELETE FROM squad_players WHERE squad_id=:squadId")
                .param("squadId", squadId).update();
        for (var slot : slots) {
            jdbcClient.sql("INSERT INTO squad_players (squad_id, slot_id, cid) VALUES (:squadId, :slotId, :cid)")
                    .param("squadId", squadId).param("slotId", slot.slotId()).param("cid", slot.cid()).update();
        }
        return findDefault(userId);
    }
}
