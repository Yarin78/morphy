package se.yarin.morphy.service.queryplans;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryPlanDebugInfo(
    @NotNull String queryDescription,
    int selectedPlanIndex,
    @Nullable Boolean allPlansAgree,
    @NotNull List<QueryPlanDto> plans) {}
