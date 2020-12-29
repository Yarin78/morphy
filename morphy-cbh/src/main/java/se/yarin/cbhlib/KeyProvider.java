package se.yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An interface for getting Chessbase encryption and decryption keys
 */
public abstract class KeyProvider {
    private static final Logger log = LoggerFactory.getLogger(KeyProvider.class);

    private static final int GROUP_COUNT = 12;

    // Using short since Java byte is signed
    private static short[][][] keyData;

    static {
        keyData = new short[GROUP_COUNT][][];
        byte[] buf = new byte[256];

        for (int i = 0; i < GROUP_COUNT; i++) {
            InputStream stream = KeyProvider.class.getResourceAsStream(String.format("keys/key_%d.bin", i));
            try {
                int noKeys = stream.available() / 256;
                keyData[i] = new short[noKeys][];
                for (int j = 0; j < noKeys; j++) {
                    keyData[i][j] = new short[256];
                    stream.read(buf);
                    for (int k = 0; k < 256; k++) {
                        keyData[i][j][k] = (short) ((256 + buf[k]) % 256); // convert to short
                    }
                }
                stream.close();
            } catch (IOException e) {
                log.error("Error reading encryption keys from resource", e);
            }
        }
    }

    public static short[] getMoveSerializationKey(int keyNo) {
        return keyData[5][keyNo];
    }

    public static void main(String[] args) throws IOException {
        // This will extract the keys from the offsets below and create binary resource files
        FileInputStream fis = new FileInputStream("testbases/CBase13.exe");
        int current = 0;
        byte[] buf = new byte[256];
        for (int group = 0; group < cb13Offsets.length; group++) {
            FileOutputStream fos = new FileOutputStream(String.format(
                    "src/main/resources/se/yarin/cbhlib/keys/key_%d.bin", group));
            for (int i = 0; i < cb13Offsets[group].length; i++) {
                int ofs = cb13Offsets[group][i];
                fis.skip(ofs - current);
                fis.read(buf);
                fos.write(buf);
                current = ofs + 256;
            }
            fos.close();
        }
        fis.close();
    }

    /**
     * These are the offsets into CBase13.exe where the keys are fetched from.
     * They were found by searching for sequences of 256 bytes that contained
     * a permutation of the list [0..256)
     * <p>
     * They are divided into groups where there are gaps in the offsets. Most groups have 32 keys.
     * <p>
     * Most (but not all) keys come in pairs - first an encryption key, then the corresponding
     * decryption key.
     */
    public static int[][] cb13Offsets = new int[][]{
            new int[]{
                0xE41620, 0xE41720, 0xE41820, 0xE41920, 0xE41A20, 0xE41B20, 0xE41C20, 0xE41D20,
                0xE41E20, 0xE41F20, 0xE42020, 0xE42120, 0xE42220, 0xE42320, 0xE42420, 0xE42520,
                0xE42620, 0xE42720, 0xE42820, 0xE42920, 0xE42A20, 0xE42B20, 0xE42C20, 0xE42D20,
                0xE42E20, 0xE42F20, 0xE43020, 0xE43120, 0xE43220, 0xE43320, 0xE43420, 0xE43520
            }, new int[]{
                0xE43F08, 0xE44008, 0xE44108, 0xE44208, 0xE44308, 0xE44408, 0xE44508, 0xE44608,
                0xE44708, 0xE44808, 0xE44908, 0xE44A08, 0xE44B08, 0xE44C08, 0xE44D08, 0xE44E08,
                0xE44F08, 0xE45008, 0xE45108, 0xE45208, 0xE45308, 0xE45408, 0xE45508, 0xE45608,
                0xE45708, 0xE45808, 0xE45908, 0xE45A08, 0xE45B08, 0xE45C08, 0xE45D08, 0xE45E08
            }, new int[]{
                0xE45FA8, 0xE460A8, 0xE461A8, 0xE462A8, 0xE463A8, 0xE464A8, 0xE465A8, 0xE466A8,
                0xE467A8, 0xE468A8, 0xE469A8, 0xE46AA8, 0xE46BA8, 0xE46CA8, 0xE46DA8, 0xE46EA8,
                0xE46FA8, 0xE470A8, 0xE471A8, 0xE472A8, 0xE473A8, 0xE474A8, 0xE475A8, 0xE476A8,
                0xE477A8, 0xE478A8, 0xE479A8, 0xE47AA8, 0xE47BA8, 0xE47CA8, 0xE47DA8, 0xE47EA8
            }, new int[]{
                0xE48450, 0xE48550, 0xE48650, 0xE48750, 0xE48850, 0xE48950, 0xE48A50, 0xE48B50,
                0xE48C50, 0xE48D50, 0xE48E50, 0xE48F50, 0xE49050, 0xE49150, 0xE49250, 0xE49350,
                0xE49450, 0xE49550, 0xE49650, 0xE49750, 0xE49850, 0xE49950, 0xE49A50, 0xE49B50,
                0xE49C50, 0xE49D50, 0xE49E50, 0xE49F50, 0xE4A050, 0xE4A150, 0xE4A250, 0xE4A350
            }, new int[]{
                0xE4BDF8, 0xE4BEF8, 0xE4BFF8, 0xE4C0F8, 0xE4C1F8, 0xE4C2F8, 0xE4C3F8, 0xE4C4F8,
                0xE4C5F8, 0xE4C6F8, 0xE4C7F8, 0xE4C8F8, 0xE4C9F8, 0xE4CAF8, 0xE4CBF8, 0xE4CCF8,
                0xE4CDF8, 0xE4CEF8, 0xE4CFF8, 0xE4D0F8, 0xE4D1F8, 0xE4D2F8, 0xE4D3F8, 0xE4D4F8,
                0xE4D5F8, 0xE4D6F8, 0xE4D7F8, 0xE4D8F8, 0xE4D9F8, 0xE4DAF8, 0xE4DBF8, 0xE4DCF8
            }, new int[]{
                0xE50B68, 0xE50C68, 0xE50D68, 0xE50E68, 0xE50F68, 0xE51068, 0xE51168, 0xE51268,
                0xE51368, 0xE51468, 0xE51568, 0xE51668, 0xE51768, 0xE51868, 0xE51968, 0xE51A68,
                0xE51B68, 0xE51C68, 0xE51D68, 0xE51E68, 0xE51F68, 0xE52068, 0xE52168, 0xE52268,
                0xE52368, 0xE52468, 0xE52568, 0xE52668, 0xE52768, 0xE52868, 0xE52968, 0xE52A68,
                0xE52B68, 0xE52C68, 0xE52D68, 0xE52E68, 0xE52F68, 0xE53068
            }, new int[]{
                0xE56710, 0xE56810, 0xE56910, 0xE56A10, 0xE56B10, 0xE56C10, 0xE56D10, 0xE56E10,
                0xE56F10, 0xE57010, 0xE57110, 0xE57210, 0xE57310, 0xE57410, 0xE57510, 0xE57610,
                0xE57710, 0xE57810, 0xE57910, 0xE57A10, 0xE57B10, 0xE57C10, 0xE57D10, 0xE57E10,
                0xE57F10, 0xE58010, 0xE58110, 0xE58210, 0xE58310, 0xE58410, 0xE58510, 0xE58610
            }, new int[]{
                0xE5A7D0, 0xE5A8D0, 0xE5A9D0, 0xE5AAD0, 0xE5ABD0, 0xE5ACD0, 0xE5ADD0, 0xE5AED0,
                0xE5AFD0, 0xE5B0D0, 0xE5B1D0, 0xE5B2D0, 0xE5B3D0, 0xE5B4D0, 0xE5B5D0, 0xE5B6D0,
                0xE5B7D0, 0xE5B8D0, 0xE5B9D0, 0xE5BAD0, 0xE5BBD0, 0xE5BCD0, 0xE5BDD0, 0xE5BED0,
                0xE5BFD0, 0xE5C0D0, 0xE5C1D0, 0xE5C2D0, 0xE5C3D0, 0xE5C4D0, 0xE5C5D0, 0xE5C6D0
            }, new int[]{
                0xE5C878, 0xE5C978, 0xE5CA78, 0xE5CB78, 0xE5CC78, 0xE5CD78, 0xE5CE78, 0xE5CF78,
                0xE5D078, 0xE5D178, 0xE5D278, 0xE5D378, 0xE5D478, 0xE5D578, 0xE5D678, 0xE5D778,
                0xE5D878, 0xE5D978, 0xE5DA78, 0xE5DB78, 0xE5DC78, 0xE5DD78, 0xE5DE78, 0xE5DF78,
                0xE5E078, 0xE5E178, 0xE5E278, 0xE5E378, 0xE5E478, 0xE5E578, 0xE5E678, 0xE5E778
            }, new int[]{
                0xE5F1C8, 0xE5F2C8, 0xE5F3C8, 0xE5F4C8, 0xE5F5C8, 0xE5F6C8, 0xE5F7C8, 0xE5F8C8,
                0xE5F9C8, 0xE5FAC8, 0xE5FBC8, 0xE5FCC8, 0xE5FDC8, 0xE5FEC8, 0xE5FFC8, 0xE600C8,
                0xE601C8, 0xE602C8, 0xE603C8, 0xE604C8, 0xE605C8, 0xE606C8, 0xE607C8, 0xE608C8,
                0xE609C8, 0xE60AC8, 0xE60BC8, 0xE60CC8, 0xE60DC8, 0xE60EC8, 0xE60FC8, 0xE610C8
            }, new int[]{
                0xE61358, 0xE61458, 0xE61558, 0xE61658, 0xE61758, 0xE61858, 0xE61958, 0xE61A58,
                0xE61B58, 0xE61C58, 0xE61D58, 0xE61E58, 0xE61F58, 0xE62058, 0xE62158, 0xE62258,
                0xE62358, 0xE62458, 0xE62558, 0xE62658, 0xE62758, 0xE62858, 0xE62958, 0xE62A58,
                0xE62B58, 0xE62C58, 0xE62D58, 0xE62E58, 0xE62F58, 0xE63058, 0xE63158, 0xE63258
            }, new int[]{
                0xE63D78, 0xE63E78, 0xE63F78, 0xE64078, 0xE64178, 0xE64278, 0xE64378, 0xE64478,
                0xE64578, 0xE64678, 0xE64778, 0xE64878, 0xE64978, 0xE64A78, 0xE64B78, 0xE64C78,
                0xE64D78, 0xE64E78, 0xE64F78, 0xE65078, 0xE65178, 0xE65278, 0xE65378, 0xE65478,
                0xE65578, 0xE65678, 0xE65778, 0xE65878, 0xE65978, 0xE65A78, 0xE65B78, 0xE65C78
            }
        };
}
