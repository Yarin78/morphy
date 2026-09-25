package se.yarin.morphy.service.annotators;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.service.entities.EntitiesService;
import se.yarin.morphy.service.entities.EntityController;

@RestController
@RequestMapping("/api/databases/{databaseId}/annotators")
public class AnnotatorsController extends EntityController<AnnotatorDto> {
  public AnnotatorsController(EntitiesService entitiesService) {
    super(entitiesService, EntityKind.ANNOTATOR);
  }
}
