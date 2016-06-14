package yarin.cbhlib.actions;

import yarin.cbhlib.AnnotatedGame;
import yarin.cbhlib.annotations.Annotation;
import yarin.chess.GameModel;

import java.util.ArrayList;

public class AddAnnotationAction extends RecordedAction {
    private ArrayList<Annotation> annotations;

    public AddAnnotationAction(ArrayList<Annotation> annotations) {
        this.annotations = annotations;
    }

    @Override
    public void apply(GameModel currentModel) {
        AnnotatedGame game = (AnnotatedGame) currentModel.getGame();
        for (Annotation annotation : annotations) {
            game.addAnnotation(currentModel.getSelectedMove(), annotation);
        }
    }
}
