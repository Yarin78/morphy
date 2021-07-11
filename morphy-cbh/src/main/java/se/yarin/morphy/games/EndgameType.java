package se.yarin.morphy.games;

import org.jetbrains.annotations.NotNull;

public enum EndgameType {
    NONE,
    ENDGAME_01, // Invalid?
    ENDGAME_02("P Pawn Endings Baurenendspiele"),
    ENDGAME_03("1..8 P :: K"),
    ENDGAME_04, // Invalid?
    ENDGAME_05("1 P :: 1 P"),
    ENDGAME_06("2 P :: 2 P"),
    ENDGAME_07("3 P :: 3 P"),
    ENDGAME_08("nP :: nP, n>3"),
    ENDGAME_09("2 P :: 1 P"),
    ENDGAME_0A("3 P :: 2 P"),
    ENDGAME_0B("4 P :: 3 P"),
    ENDGAME_0C("5 P :: 4 P"),
    ENDGAME_0D("n P :: n-1 P"),
    ENDGAME_0E("n P :: n-2 P"),
    ENDGAME_0F("n P :: n-3 P"),
    ENDGAME_10("RP Pure Rook Endings Reine Turmendspiele"),
    ENDGAME_11("R :: R (Endgame CD1, Fritz CD)"),
    ENDGAME_12, // Invalid?
    ENDGAME_13("R + 1P (a,g) :: R (Endgame CD1, Fritz CD)"),
    ENDGAME_14("R + 1P (b,g) :: R (Endgame CD1, Fritz CD)"),
    ENDGAME_15("R + 1P (c,f) :: R (Endgame CD1, Fritz CD)"),
    ENDGAME_16("R + 1P (d,e) :: R (Endgame CD1, Fritz CD)"),
    ENDGAME_17("R + 2P :: R"),
    ENDGAME_18("R + nP :: R, n>2"),
    ENDGAME_19("R + 2P :: R + 1P"),
    ENDGAME_1A("R + 3P :: R + 2P"),
    ENDGAME_1B("R + 4P :: R + 3P"),
    ENDGAME_1C("R + nP :: R + (n - 1)P, n>4"),
    ENDGAME_1D("R + nP :: R + (n-2)P, n>2"),
    ENDGAME_1E("R + nP :: R + (n - m)P, n>3, m>2"),
    ENDGAME_1F("R + 1P :: R + 1P"),
    ENDGAME_20("R + 2P :: R + 2P"),
    ENDGAME_21("R + 3P :: R + 3P"),
    ENDGAME_22("R + 4P :: R + 4P"),
    ENDGAME_23("R + nP :: R + nP, n>4"),
    ENDGAME_24("R :: P/N/B Rook vs. Other Pieces"),
    ENDGAME_25("R :: P"),
    ENDGAME_26, // Invalid?
    ENDGAME_27("R :: N"),
    ENDGAME_28("R :: B"),
    ENDGAME_29("R :: NN/NB/BB"),
    ENDGAME_2A("R :: NNN/NNB/NBB/BBB"),
    ENDGAME_2B("RN/RB Rook and Minor Piece"),
    ENDGAME_2C("RN/RB :: P"),
    ENDGAME_2D, // Invalid?
    ENDGAME_2E("RN/RB :: N/B"),
    ENDGAME_2F("RN :: R"),
    ENDGAME_30("RB :: R"),
    ENDGAME_31("RN/RB :: NN/NB/BB"),
    ENDGAME_32("RN :: RN"),
    ENDGAME_33("RB :: RN"),
    ENDGAME_34("RB :: RB!"),
    ENDGAME_35("RB :: RB="),
    ENDGAME_36("RR Two rooks"),
    ENDGAME_37("RR :: P"),
    ENDGAME_38, // Invalid?
    ENDGAME_39("RR :: R"),
    ENDGAME_3A("RR : N/B"),
    ENDGAME_3B("RR :: NN/NB/BB"),
    ENDGAME_3C("RR :: RN"),
    ENDGAME_3D("RR :: RB"),
    ENDGAME_3E("RR :: RR"),
    ENDGAME_3F("QP Pure Queen Endings"),
    ENDGAME_40("Q :: Q (Endgame CD1, Fritz CD)"),
    ENDGAME_41, // Invalid?
    ENDGAME_42("Q + P(ah) :: Q (Endgame CD1, Fritz CD)"),
    ENDGAME_43("Q + P(bg) :: Q (Endgame CD1, Fritz CD)"),
    ENDGAME_44("Q + P(cf) :: Q (Endgame CD1, Fritz CD)"),
    ENDGAME_45("Q + P(de) :: Q (Endgame CD1, Fritz CD)"),
    ENDGAME_46("Q + PP :: Q"),
    ENDGAME_47("Q + P-P :: Q"),
    ENDGAME_48("Q + nP :: Q"),
    ENDGAME_49("Q + 2P :: Q + P"),
    ENDGAME_4A("Q + 3P :: Q + 2P"),
    ENDGAME_4B("Q + nP :: Q + (n-1)P"),
    ENDGAME_4C("Q + nP :: Q + (n - m)P; n>2, m>1"),
    ENDGAME_4D("Q + P :: Q + P"),
    ENDGAME_4E("Q + 2P :: Q + 2P"),
    ENDGAME_4F("Q + nP :: Q + nP"),
    ENDGAME_50("Q :: P/N/B/R Queen vs. Other Pieces"),
    ENDGAME_51("Q :: nP"),
    ENDGAME_52, // Invalid?
    ENDGAME_53("Q :: N"),
    ENDGAME_54("Q :: B"),
    ENDGAME_55("Q :: R"),
    ENDGAME_56("Q :: NN/NB/BB"),
    ENDGAME_57("Q :: RN/RB"),
    ENDGAME_58("Q :: RR"),
    ENDGAME_59("Q :: NNN/NNB/NBB/BBB"),
    ENDGAME_5A("Q :: RNN/RNB/RBB"),
    ENDGAME_5B("Q :: RRN/RRB"),
    ENDGAME_5C("QB/QN/QR Queen and One Piece"),
    ENDGAME_5D("QN/QB :: P"),
    ENDGAME_5E, // Invalid?
    ENDGAME_5F("QN/QB :: N/B"),
    ENDGAME_60("QN/QB :: R"),
    ENDGAME_61("QN :: Q"),
    ENDGAME_62("QN + nP :: Q"),
    ENDGAME_63("QN :: Q + nP"),
    ENDGAME_64("QN + nP :: Q + nP"),
    ENDGAME_65("QB :: Q"),
    ENDGAME_66("QB + nP :: Q"),
    ENDGAME_67("QB :: Q + nP"),
    ENDGAME_68("QB + nP :: Q + nP"),
    ENDGAME_69("QN/QB :: NN/NB/BB"),
    ENDGAME_6A("QN/QB :: RN/RB"),
    ENDGAME_6B("QN/QB :: RR"),
    ENDGAME_6C("QN :: QN"),
    ENDGAME_6D("QB :: QN"),
    ENDGAME_6E("QB :: QB (!)"),
    ENDGAME_6F("QB :: QB (=)"),
    ENDGAME_70("QR :: Q"),
    ENDGAME_71("QR :: QN"),
    ENDGAME_72("QR :: QB"),
    ENDGAME_73("N/B One Minor Piece"),
    ENDGAME_74("N :: K"),
    ENDGAME_75, // Invalid?
    ENDGAME_76("N :: P"),
    ENDGAME_77("B :: K"),
    ENDGAME_78("B :: P"),
    ENDGAME_79("N :: N"),
    ENDGAME_7A("B + nP :: N"),
    ENDGAME_7B("B :: N + nP"),
    ENDGAME_7C("B + nP :: N + (n + m)P; n>0, m>0"),
    ENDGAME_7D("B + (n + m)P :: N + nP"),
    ENDGAME_7E("B + nP : N + nP"),
    ENDGAME_7F("B :: B!"),
    ENDGAME_80("B :: B="),
    ENDGAME_81("NN/NB/BB Two Minor Pieces"),
    ENDGAME_82("NN/BN/BB : K"),
    ENDGAME_83, // Invalid?
    ENDGAME_84("NN/BN/BB :: P"),
    ENDGAME_85("NN/BN/BB :: N/B"),
    ENDGAME_86("NN :: NN"),
    ENDGAME_87("NB :: NN"),
    ENDGAME_88("NB :: NB"),
    ENDGAME_89("BB :: NN"),
    ENDGAME_8A("BB :: NB"),
    ENDGAME_8B("BB :: BB");

    private String description;

    public String getDescription() {
        return description;
    }

    EndgameType(String description) {
        this.description = description;
    }

    EndgameType() {
        this.description = "?";
    }

    public static EndgameType decode(int value) {
        if (value < 0 || value >= EndgameType.values().length) {
            return EndgameType.NONE;
        }
        return EndgameType.values()[value];
    }

    public static int encode(@NotNull EndgameType endgameType) {
        return endgameType.ordinal();
    }
}
