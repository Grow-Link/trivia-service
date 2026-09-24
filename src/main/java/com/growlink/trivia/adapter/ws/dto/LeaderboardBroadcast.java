package com.growlink.trivia.adapter.ws.dto;

import java.util.List;

public record LeaderboardBroadcast(String type, String codigo, List<LeaderboardEntry> ranking) {
    public static LeaderboardBroadcast of(String codigo, List<LeaderboardEntry> ranking) {
        return new LeaderboardBroadcast("LEADERBOARD", codigo, ranking);
    }
}
