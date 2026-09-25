package se.yarin.morphy.service.players;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.service.entities.EntitiesService;
import se.yarin.morphy.service.entities.EntityController;

@RestController
@RequestMapping("/api/databases/{databaseId}/players")
public class PlayersController extends EntityController<PlayerDto> {
  public PlayersController(EntitiesService entitiesService) {
    super(entitiesService, EntityKind.PLAYER);
  }
}
