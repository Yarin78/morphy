package yarin.cbhlib;

import yarin.cbhlib.exceptions.CBHException;
import yarin.cbhlib.exceptions.CBHFormatException;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Represents a game header record in the CBJ file.
 * This is an extension of the data in the CBH file.
 */
public class GameHeaderExtra {
    private int whitePlayerTeamId;
    private int blackPlayerTeamId;
    public RatingDetails whiteRatingDetails;
    public RatingDetails blackRatingDetails;

    public int getWhitePlayerTeamId() {
        return whitePlayerTeamId;
    }

    public int getBlackPlayerTeamId() {
        return blackPlayerTeamId;
    }

    public RatingDetails getWhiteRatingDetails() {
        return whiteRatingDetails;
    }

    public RatingDetails getBlackRatingDetails() {
        return blackRatingDetails;
    }

    /**
     * Internal constructor used when loading a secondary game header from a CBJ database.
     *
     * @param cbjData  The binary record data
     * @throws CBHFormatException
     */
    GameHeaderExtra(ByteBuffer cbjData, int version) throws CBHException, IOException {
        whitePlayerTeamId = cbjData.getInt(0);
        blackPlayerTeamId = cbjData.getInt(4);
        // Not sure if this is correct really, but in CB12 this data doesn't exist
        if (version >= 11) {
            whiteRatingDetails = new RatingDetails(cbjData, 39);
            blackRatingDetails = new RatingDetails(cbjData, 55);
        } else {
            whiteRatingDetails = new RatingDetails();
            blackRatingDetails = new RatingDetails();
        }
    }

    public GameHeaderExtra() {
        this.whitePlayerTeamId = -1;
        this.blackPlayerTeamId = -1;
        this.whiteRatingDetails = new RatingDetails();
        this.blackRatingDetails = new RatingDetails();
    }
}
