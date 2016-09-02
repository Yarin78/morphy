package se.yarin.cbhlib;

import se.yarin.chess.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static se.yarin.chess.Chess.*;

public class ExploreWeirdMoveEncodings {
/*
    "1. e4 Nc6 2. e5 f5 3. exf6 Nxf6 4. c4 g6 5. c5 b5 6. cxb6 Bg7 7. b7 O-O 8.\n"+
            "bxa8=R Ba6 9. Nc3 h5 10. d3 h4 11. Bf4 g5 12. Qd2 gxf4 13. O-O-O Ne5 14. g3\n"+
            "hxg3 15. hxg3 fxg3 16. d4 gxf2 17. dxe5 fxg1=B 18. exf6 Be3 19. fxe7 Bc4 20.\n"+
            "exd8=R a5 21. Ra6 a4 22. Rah6 a3 23. Rxd7 axb2+ 24. Kc2 b1=B+ 25. Kb2 Bf5 26.\n"+
            "Re1 Rb8+ 27. Ka1 Bxd2 28. Rh8+ Bxh8 29. Rxh8+ Kxh8 30. Ree7 Bxc3# *\n"+
            "\n"
            */

    public static void main(String[] args) throws IOException {
        Database db = Database.create(new File("testbases/tmp/Weird Move Encodings/.cbh"));
    }

    public static void main2(String[] args) throws IOException {
        Database db = Database.create(new File("testbases/tmp/Weird Move Encodings/multimodes2.cbh"));
/*
        byte[] moveData = convert("85 E2 56 6B FB B7 1F 35 56 BC 1F DF 49 85 1F 9B FB 9D 1F 43 9F B6 D4 C0 30 38 85 15 30 7F 6C 6D 6C C0 11 98 85 E5 AA ED 30 9E B6 B5 6B BD 78 7F 78 FA 67 3E 78 A4 75 65 78 6C 75 81 75 2A 31 41 26 12 31 C6 C8 B2 92 05 31 B1 C8 54 C8 B9 12 49 60 4C A0 AA A9 FB D1 48 5F 3F 4B E9 60 B8 4E 55 4B 7A 96 13 A0 A2 5F 21 A6 DD EB DC AB B3");
        GameHeaderModel header = new GameHeaderModel();
        GameModel gameModel = new GameModel(header, new GameMovesModel());
        header.setField("white", String.format("data"));
        db.addGameRawMoves(gameModel, 0x85, moveData);

*/

        for (int i = 0; i < 8; i++) {
            db.getMovesBase().setEncodingMode(i);

            GameHeaderModel header = new GameHeaderModel();
            GameModel gameModel = new GameModel(header, new GameMovesModel());

            gameModel.header().setField("white", "Mode " + i);

            gameModel.moves().root()
                    .addMove(E2, E4).addMove(C7, C5).addMove(G1, F3).addMove(D7, D6)
                    .addMove(D2, D4).addMove(C5, D4).addMove(F3, D4).addMove(G8, F6)
                    .addMove(B1, C3).addMove(B8, C6).addMove(D4, C6).addMove(B7, C6)
                    .addMove(F1, E2).addMove(E7, E6).addMove(E1, G1).addMove(F8, E7);

            db.addGame(gameModel);
        }

        db.close();
    }

    private static byte[] convert(String s) {
        s = s.replaceAll(" ", "");
        byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(s.substring(i*2, i*2+2), 16);
        }
        return bytes;
    }

    public static void main1(String[] args) throws ChessBaseInvalidDataException, ChessBaseUnsupportedException {
//        byte[] data = {0x02, 0x00, 0x00, 0x00,  0x05, 0x7A}; // a4 e5
//        byte[] data = {0x02, 0x00, 0x00, 0x00,  0x06, 0x58}; // Bc4 xxx
//        byte[] data = {0x02, 0x00, 0x00, 0x00,  0x0E, 0x6A}; // Bc4 xxx
//        byte[] data = {0x02, 0x00, 0x00, 0x00, (byte) 0xF6, 0x30, (byte) 0xB9, (byte) 0xE7};
//        byte[] data = {0x02, 0x00, 0x00, 0x00, (byte) 0xEB, 0x4F, (byte) 0x46, (byte) 0xF0};
//        byte[] data = {0x02, 0x00, 0x00, 0x00, 0x4D, 0x78, (byte) 0xA0, 0x1F};
        byte[] data = {
            0x01,0x00,0x00,0x20, (byte) 0xb5,0x6f, (byte) 0xec, (byte) 0x81,0x42, (byte) 0xc1, (byte) 0xc0, (byte) 0x9d,0x14, (byte) 0xf5, (byte) 0xc5,0x5a,0x5d,0x2d, (byte) 0xb3,0x29, (byte) 0xc5,0x76,
                 0x19,0x2c, (byte) 0xfa,0x38,0x1c, (byte) 0xcc,0x66,0x5a, (byte) 0xa7,0x7a};
        GameMovesModel moves = MovesSerializer.deserializeMoves(ByteBuffer.wrap(data));
        System.out.println(moves);


    }

    public static void maina(String[] args) throws IOException {
        Database db = Database.create(new File("testbases/tmp/Weird Move Encodings/var2.cbh"));
        db.getMovesBase().setEncodingMode(3);
        GameMovesModel moves = new GameMovesModel();
//        moves.root().addMove(E2, E4).addMove(D7, D5).addMove(G1, F3).parent().addMove(B1, C3).parent().addMove(E4, D5).addMove(D8, D5);
        moves.root().addMove(E2, E4).addMove(E7, E5).addMove(G1, F3).addMove(B8, C6)
                .parent().parent().addMove(B1, C3).addMove(B8, C6);

        GameHeaderModel header = new GameHeaderModel();
        GameModel gameModel = new GameModel(header, moves);
        db.addGame(gameModel);
        db.close();
    }

    public static void mainx(String[] args) throws IOException, ChessBaseException {
        Database db = Database.open(new File("testbases/tmp/Weird Move Encodings/mode1var15.cbh"));
        GameModel gameModel = db.getGameModel(1);
        System.out.println(gameModel.moves());
        db.close();
    }

    public static void main4(String[] args) throws IOException {
        Database db = Database.create(new File("testbases/tmp/Weird Move Encodings/mode1prom4.cbh"));
        db.getMovesBase().setEncodingMode(1);

        for (int i = 0; i < 4; i++) {
            Stone stone = Stone.NO_STONE;
            switch (i) {
                case 0 : stone = Stone.WHITE_QUEEN; break;
                case 1 : stone = Stone.WHITE_KNIGHT; break;
                case 2 : stone = Stone.WHITE_ROOK; break;
                case 3 : stone = Stone.WHITE_BISHOP; break;
            }

            GameMovesModel moves = new GameMovesModel();
            moves.root()
                    .addMove(A2, A4).addMove(G8, F6).addMove(A4, A5).addMove(F6, G8)
                    .addMove(A5, A6).addMove(G8, F6).addMove(A6, B7).addMove(F6, G8)
                    .addMove(B7, A8, stone);


            GameHeaderModel header = new GameHeaderModel();
            header.setField("white", stone.toString());
            GameModel gameModel = new GameModel(header, moves);
            db.addGame(gameModel);
        }

        db.close();
    }

    public static void main3(String[] args) throws IOException {
        for (int i = 0; i < keyOffset.length; i++) {
            try {
                int[] decryptMap = getKey(keyOffset[i]);
//                System.out.println(String.format("Key %d at %X is OK", i, keyOffset[i]));

//                byte[] input = new byte[] {(byte) 0xFF, (byte) 0xDB, 0x0E};
                byte[] input = new byte[] {0x05};
                byte[] expectedOutput = new byte[] {(byte) 0x70};
                int modifier = 0;
                boolean match = true;
                for (int j = 0; j < input.length; j++) {
                    byte b = input[j];
//                    int d = decryptMap[(b + modifier + 256) % 256];
                    int d = (decryptMap[(b + 256) % 256] + modifier) % 256;

                    if ((byte) d != expectedOutput[j]) match = false;
                    modifier = (modifier + 255) % 256;
                }
                if (match) {
                    System.out.println(String.format("Key %d at %X is a match!", i, keyOffset[i]));
                }

            } catch (IllegalArgumentException e) {
                System.err.println(String.format("Skipping key at %X", keyOffset[i]));
//                System.out.println(String.format("Key %d at %X is NOT followed by decrypt key", i, keyOffset[i]));
            }
        }

/*
        byte[] data = new byte[] {(byte) 0xFF, (byte) 0xDB, 0x0E};
        int modifier = 0;
        for (byte b : data) {
            int d = decryptMap[(b + modifier + 256) % 256];
            System.out.println(String.format("%02X", d));
            modifier = (modifier + 255) % 256;
        }
        */
    }

    public static int[] getKey(int ofs) throws IOException {
        byte[] encryptMap = new byte[256];
//        byte[] expectedDecryptMap = new byte[256];
        int[] decryptMap = new int[256];

        FileInputStream stream = new FileInputStream("/Users/yarin/src/cbhlib/testbases/CBase13.exe");
        stream.skip(ofs);

        stream.read(encryptMap);
//        stream.read(expectedDecryptMap);
        for (int i = 0; i < 256; i++) {
            decryptMap[(encryptMap[i] + 256) % 256] = i;
        }

//        for (int i = 0; i < 256; i++) {
//            if ((byte) decryptMap[i] != expectedDecryptMap[i]) {
//                throw new IllegalArgumentException("The decrypt map didn't follow the encrypt map");
//            }
//        }

        return decryptMap;
    }

    public static int[] keyOffset = new int[] {
            0xE41620, 0xE41720, 0xE41820, 0xE41920, 0xE41A20, 0xE41B20, 0xE41C20, 0xE41D20,
            0xE41E20, 0xE41F20, 0xE42020, 0xE42120, 0xE42220, 0xE42320, 0xE42420, 0xE42520,
            0xE42620, 0xE42720, 0xE42820, 0xE42920, 0xE42A20, 0xE42B20, 0xE42C20, 0xE42D20,
            0xE42E20, 0xE42F20, 0xE43020, 0xE43120, 0xE43220, 0xE43320, 0xE43420, 0xE43520,

            0xE43F08, 0xE44008, 0xE44108, 0xE44208, 0xE44308, 0xE44408, 0xE44508, 0xE44608,
            0xE44708, 0xE44808, 0xE44908, 0xE44A08, 0xE44B08, 0xE44C08, 0xE44D08, 0xE44E08,
            0xE44F08, 0xE45008, 0xE45108, 0xE45208, 0xE45308, 0xE45408, 0xE45508, 0xE45608,
            0xE45708, 0xE45808, 0xE45908, 0xE45A08, 0xE45B08, 0xE45C08, 0xE45D08, 0xE45E08,

            0xE45FA8, 0xE460A8, 0xE461A8, 0xE462A8, 0xE463A8, 0xE464A8, 0xE465A8, 0xE466A8,
            0xE467A8, 0xE468A8, 0xE469A8, 0xE46AA8, 0xE46BA8, 0xE46CA8, 0xE46DA8, 0xE46EA8,
            0xE46FA8, 0xE470A8, 0xE471A8, 0xE472A8, 0xE473A8, 0xE474A8, 0xE475A8, 0xE476A8,
            0xE477A8, 0xE478A8, 0xE479A8, 0xE47AA8, 0xE47BA8, 0xE47CA8, 0xE47DA8, 0xE47EA8,

            0xE48450, 0xE48550, 0xE48650, 0xE48750, 0xE48850, 0xE48950, 0xE48A50, 0xE48B50,
            0xE48C50, 0xE48D50, 0xE48E50, 0xE48F50, 0xE49050, 0xE49150, 0xE49250, 0xE49350,
            0xE49450, 0xE49550, 0xE49650, 0xE49750, 0xE49850, 0xE49950, 0xE49A50, 0xE49B50,
            0xE49C50, 0xE49D50, 0xE49E50, 0xE49F50, 0xE4A050, 0xE4A150, 0xE4A250, 0xE4A350,

            0xE4BDF8, 0xE4BEF8, 0xE4BFF8, 0xE4C0F8, 0xE4C1F8, 0xE4C2F8, 0xE4C3F8, 0xE4C4F8,
            0xE4C5F8, 0xE4C6F8, 0xE4C7F8, 0xE4C8F8, 0xE4C9F8, 0xE4CAF8, 0xE4CBF8, 0xE4CCF8,
            0xE4CDF8, 0xE4CEF8, 0xE4CFF8, 0xE4D0F8, 0xE4D1F8, 0xE4D2F8, 0xE4D3F8, 0xE4D4F8,
            0xE4D5F8, 0xE4D6F8, 0xE4D7F8, 0xE4D8F8, 0xE4D9F8, 0xE4DAF8, 0xE4DBF8, 0xE4DCF8,

            0xE50B68, 0xE50C68, 0xE50D68, 0xE50E68, 0xE50F68, 0xE51068, 0xE51168, 0xE51268,
            0xE51368, 0xE51468, 0xE51568, 0xE51668, 0xE51768, 0xE51868, 0xE51968, 0xE51A68,
            0xE51B68, 0xE51C68, 0xE51D68, 0xE51E68, 0xE51F68, 0xE52068, 0xE52168, 0xE52268,
            0xE52368, 0xE52468, 0xE52568, 0xE52668, 0xE52768, 0xE52868, 0xE52968, 0xE52A68,
            0xE52B68, 0xE52C68, 0xE52D68, 0xE52E68, 0xE52F68, 0xE53068,

            0xE56710, 0xE56810, 0xE56910, 0xE56A10, 0xE56B10, 0xE56C10, 0xE56D10, 0xE56E10,
            0xE56F10, 0xE57010, 0xE57110, 0xE57210, 0xE57310, 0xE57410, 0xE57510, 0xE57610,
            0xE57710, 0xE57810, 0xE57910, 0xE57A10, 0xE57B10, 0xE57C10, 0xE57D10, 0xE57E10,
            0xE57F10, 0xE58010, 0xE58110, 0xE58210, 0xE58310, 0xE58410, 0xE58510, 0xE58610,

            0xE5A7D0, 0xE5A8D0, 0xE5A9D0, 0xE5AAD0, 0xE5ABD0, 0xE5ACD0, 0xE5ADD0, 0xE5AED0,
            0xE5AFD0, 0xE5B0D0, 0xE5B1D0, 0xE5B2D0, 0xE5B3D0, 0xE5B4D0, 0xE5B5D0, 0xE5B6D0,
            0xE5B7D0, 0xE5B8D0, 0xE5B9D0, 0xE5BAD0, 0xE5BBD0, 0xE5BCD0, 0xE5BDD0, 0xE5BED0,
            0xE5BFD0, 0xE5C0D0, 0xE5C1D0, 0xE5C2D0, 0xE5C3D0, 0xE5C4D0, 0xE5C5D0, 0xE5C6D0,

            0xE5C878, 0xE5C978, 0xE5CA78, 0xE5CB78, 0xE5CC78, 0xE5CD78, 0xE5CE78, 0xE5CF78,
            0xE5D078, 0xE5D178, 0xE5D278, 0xE5D378, 0xE5D478, 0xE5D578, 0xE5D678, 0xE5D778,
            0xE5D878, 0xE5D978, 0xE5DA78, 0xE5DB78, 0xE5DC78, 0xE5DD78, 0xE5DE78, 0xE5DF78,
            0xE5E078, 0xE5E178, 0xE5E278, 0xE5E378, 0xE5E478, 0xE5E578, 0xE5E678, 0xE5E778,

            0xE5F1C8, 0xE5F2C8, 0xE5F3C8, 0xE5F4C8, 0xE5F5C8, 0xE5F6C8, 0xE5F7C8, 0xE5F8C8,
            0xE5F9C8, 0xE5FAC8, 0xE5FBC8, 0xE5FCC8, 0xE5FDC8, 0xE5FEC8, 0xE5FFC8, 0xE600C8,
            0xE601C8, 0xE602C8, 0xE603C8, 0xE604C8, 0xE605C8, 0xE606C8, 0xE607C8, 0xE608C8,
            0xE609C8, 0xE60AC8, 0xE60BC8, 0xE60CC8, 0xE60DC8, 0xE60EC8, 0xE60FC8, 0xE610C8,

            0xE61358, 0xE61458, 0xE61558, 0xE61658, 0xE61758, 0xE61858, 0xE61958, 0xE61A58,
            0xE61B58, 0xE61C58, 0xE61D58, 0xE61E58, 0xE61F58, 0xE62058, 0xE62158, 0xE62258,
            0xE62358, 0xE62458, 0xE62558, 0xE62658, 0xE62758, 0xE62858, 0xE62958, 0xE62A58,
            0xE62B58, 0xE62C58, 0xE62D58, 0xE62E58, 0xE62F58, 0xE63058, 0xE63158, 0xE63258,

            0xE63D78, 0xE63E78, 0xE63F78, 0xE64078, 0xE64178, 0xE64278, 0xE64378, 0xE64478,
            0xE64578, 0xE64678, 0xE64778, 0xE64878, 0xE64978, 0xE64A78, 0xE64B78, 0xE64C78,
            0xE64D78, 0xE64E78, 0xE64F78, 0xE65078, 0xE65178, 0xE65278, 0xE65378, 0xE65478,
            0xE65578, 0xE65678, 0xE65778, 0xE65878, 0xE65978, 0xE65A78, 0xE65B78, 0xE65C78
    };
}
