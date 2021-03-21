package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.TeamEntity;
import se.yarin.cbhlib.games.SerializedExtendedGameHeaderFilter;
import se.yarin.util.ByteBufferUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TeamFilter extends SearchFilterBase implements SerializedExtendedGameHeaderFilter {
    private static final int MAX_TEAMS = 20;

    private final String searchString;
    private List<TeamEntity> teams;
    private HashSet<Integer> teamIds;

    public TeamFilter(Database database, TeamEntity team) {
        super(database);

        this.searchString = "";
        this.teams = Arrays.asList(team);
    }

    public TeamFilter(Database database, String searchString) {
        super(database);

        this.searchString = searchString;
    }

    public void initSearch() {
        if (this.teams == null) {
            // If we can quickly determine if there are few enough tournaments in the database that matches the search string,
            // we can get an improved searched
            Stream<TeamEntity> stream = getDatabase().getTeamBase().prefixSearch(searchString);
            List<TeamEntity> matchingTeams = stream.limit(MAX_TEAMS + 1).collect(Collectors.toList());
            if (matchingTeams.size() <= MAX_TEAMS) {
                this.teams = matchingTeams;
            }
        }

        if (this.teams != null) {
            this.teamIds = this.teams.stream().map(TeamEntity::getId).collect(Collectors.toCollection(HashSet::new));
        }
    }

    @Override
    public int countEstimate() {
        if (teams == null) {
            return SearchFilter.UNKNOWN_COUNT_ESTIMATE;
        } else {
            int count = 0;
            for (TeamEntity team : teams) {
                count += team.getCount();
            }
            return count;
        }
    }

    @Override
    public int firstGameId() {
        if (teams == null) {
            return 1;
        } else {
            int first = Integer.MAX_VALUE;
            for (TeamEntity team : teams) {
                first = Math.min(first, team.getFirstGameId());
            }
            return first;
        }
    }

    @Override
    public boolean matches(Game game) {
        TeamEntity whiteTeam = game.getWhiteTeam();
        TeamEntity blackTeam = game.getBlackTeam();
        if (this.teams != null) {
            return this.teams.contains(whiteTeam) || this.teams.contains(blackTeam);
        } else {
            if (whiteTeam != null && whiteTeam.getTitle().startsWith(this.searchString)) {
                return true;
            }
            if (blackTeam != null && blackTeam.getTitle().startsWith(this.searchString)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(byte[] serializedExtendedGameHeader) {
        if (teamIds != null) {
            int whiteTeamId = ByteBufferUtil.getIntB(serializedExtendedGameHeader, 0);
            int blackTeamId = ByteBufferUtil.getIntB(serializedExtendedGameHeader, 4);
            return teamIds.contains(whiteTeamId) || teamIds.contains(blackTeamId);
        }
        return false;
    }
}
