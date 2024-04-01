package se.yarin.morphy.queries;

import se.yarin.morphy.IdObject;

public abstract class EntityQueryPlanner<T extends IdObject> {
    private QueryPlanner queryPlanner;

    public QueryPlanner getQueryPlanner() {
        return queryPlanner;
    }

    public void setQueryPlanner(QueryPlanner queryPlanner) {
        this.queryPlanner = queryPlanner;
    }

    public EntityQueryPlanner(QueryPlanner queryPlanner) {
        this.queryPlanner = queryPlanner;
    }
}
