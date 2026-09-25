package se.yarin.morphy.service.sources;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.service.entities.EntitiesService;
import se.yarin.morphy.service.entities.EntityController;

@RestController
@RequestMapping("/api/databases/{databaseId}/sources")
public class SourcesController extends EntityController<SourceDto> {
  public SourcesController(EntitiesService entitiesService) {
    super(entitiesService, EntityKind.SOURCE);
  }
}
