package se.yarin.morphy.service.tournaments;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.model.TournamentDto;
import se.yarin.morphy.service.entities.EntitiesService;
import se.yarin.morphy.service.entities.EntityController;

@RestController
@RequestMapping("/api/databases/{databaseId}/tournaments")
public class TournamentsController extends EntityController<TournamentDto> {
  public TournamentsController(EntitiesService entitiesService) {
    super(entitiesService, EntityKind.TOURNAMENT);
  }
}
