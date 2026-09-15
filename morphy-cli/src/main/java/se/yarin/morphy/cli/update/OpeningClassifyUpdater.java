package se.yarin.morphy.cli.update;

import se.yarin.chess.GameModel;
import se.yarin.morphy.cli.opening.OpeningRepertoireCache;

import java.util.Optional;

/**
 * Classifies a game against an opening repertoire, sets its GameTag to the best matching branch,
 * and annotates moves that deviate from it. Games that don't match any entry in the repertoire
 * are skipped.
 */
public class OpeningClassifyUpdater implements GameUpdater {
  private final OpeningRepertoireCache repertoire;

  public OpeningClassifyUpdater(OpeningRepertoireCache repertoire) {
    this.repertoire = repertoire;
  }

  @Override
  public boolean apply(GameModel model) {
    Optional<OpeningRepertoireCache.Entry> matchedEntry = repertoire.classify(model.moves());
    if (matchedEntry.isEmpty()) {
      return false;
    }
    OpeningRepertoireCache.Entry entry = matchedEntry.get();
    repertoire.annotate(model.moves(), entry);
    model.header().setGameTag(repertoire.formatTag(entry));
    return true;
  }
}
