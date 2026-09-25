package se.yarin.morphy.service.gametags;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.service.entities.EntitiesService;
import se.yarin.morphy.service.entities.EntityController;

@RestController
@RequestMapping("/api/databases/{databaseId}/gametags")
public class GameTagsController extends EntityController<GameTagDto> {
  public GameTagsController(EntitiesService entitiesService) {
    super(entitiesService, EntityKind.GAME_TAG);
  }
}
