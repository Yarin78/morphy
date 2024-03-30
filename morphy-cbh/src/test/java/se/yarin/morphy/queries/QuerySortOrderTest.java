package se.yarin.morphy.queries;

import org.junit.Test;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;

import java.util.List;

import static org.junit.Assert.*;

public class QuerySortOrderTest {
    @Test
    public void byIdIsSameOrStrongerAsbyId() {
        QuerySortOrder<Player> a = QuerySortOrder.byId();
        QuerySortOrder<Player> b = QuerySortOrder.byId();
        assertTrue(a.isSameOrStronger(b));
    }

    @Test
    public void byPlayerDefaultIndexIsSameOrStrongerAsByPlayerDefaultIndex() {
        QuerySortOrder<Player> a = QuerySortOrder.byPlayerDefaultIndex();
        QuerySortOrder<Player> b = QuerySortOrder.byPlayerDefaultIndex();
        assertTrue(a.isSameOrStronger(b));
    }

    @Test
    public void byTournamentDefaultIndexIsSameOrStrongerAsByTournamentDefaultIndex() {
        QuerySortOrder<Tournament> a = QuerySortOrder.byTournamentDefaultIndex();
        QuerySortOrder<Tournament> b = QuerySortOrder.byTournamentDefaultIndex();
        assertTrue(a.isSameOrStronger(b));
    }

    @Test
    public void byIdIsNotSameOrStrongerAsByPlayerDefaultIndex() {
        QuerySortOrder<Player> a = QuerySortOrder.byId();
        QuerySortOrder<Player> b = QuerySortOrder.byPlayerDefaultIndex();
        assertFalse(a.isSameOrStronger(b));
        assertFalse(b.isSameOrStronger(a));
    }

    @Test
    public void byTournamentDefaultIndexIsSameOrStrongerAsByTournamentYear() {
        QuerySortOrder<Tournament> a = QuerySortOrder.byTournamentDefaultIndex();
        QuerySortOrder<Tournament> b = new QuerySortOrder<>(
                List.of(QuerySortField.tournamentYear()),
                List.of(QuerySortOrder.Direction.DESCENDING)
        );
        assertTrue(a.isSameOrStronger(b));
        assertFalse(b.isSameOrStronger(a));
    }

    @Test
    public void byTournamentDefaultIndexIsNotSameOrStrongerAsByTournamentYearReversed() {
        QuerySortOrder<Tournament> a = QuerySortOrder.byTournamentDefaultIndex();
        QuerySortOrder<Tournament> b = new QuerySortOrder<>(
                List.of(QuerySortField.tournamentYear()),
                List.of(QuerySortOrder.Direction.ASCENDING)
        );
        assertFalse(a.isSameOrStronger(b));
        assertFalse(b.isSameOrStronger(a));
    }

    @Test
    public void byTournamentDefaultIndexIsNotSameOrStrongerAsByTournamentTitle() {
        QuerySortOrder<Tournament> a = QuerySortOrder.byTournamentDefaultIndex();
        QuerySortOrder<Tournament> b = new QuerySortOrder<>(
                List.of(QuerySortField.tournamentTitle()),
                List.of(QuerySortOrder.Direction.ASCENDING)
        );
        assertFalse(a.isSameOrStronger(b));
        assertFalse(b.isSameOrStronger(a));
    }

    @Test
    public void byIdIsStrongerThanNoSorting() {
        QuerySortOrder<Player> a = QuerySortOrder.byId();
        QuerySortOrder<Player> b = QuerySortOrder.none();
        assertTrue(a.isSameOrStronger(b));
        assertFalse(b.isSameOrStronger(a));
    }

    @Test
    public void byPlayerNameIsStrongThanNoSorting() {
        QuerySortOrder<Player> a = QuerySortOrder.byPlayerDefaultIndex();
        QuerySortOrder<Player> b = QuerySortOrder.none();
        assertTrue(a.isSameOrStronger(b));
        assertFalse(b.isSameOrStronger(a));
    }
}
