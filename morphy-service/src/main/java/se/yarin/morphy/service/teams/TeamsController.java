package se.yarin.morphy.service.teams;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.service.entities.EntitiesService;
import se.yarin.morphy.service.entities.EntityController;

@RestController
@RequestMapping("/api/databases/{databaseId}/teams")
public class TeamsController extends EntityController<TeamDto> {
  public TeamsController(EntitiesService entitiesService) {
    super(entitiesService, EntityKind.TEAM);
  }
}
