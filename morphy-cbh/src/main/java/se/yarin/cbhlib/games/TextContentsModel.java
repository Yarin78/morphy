package se.yarin.cbhlib.games;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.moves.ChessBaseMoveDecodingException;
import se.yarin.util.ByteBufferUtil;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * A model of a Text entry in a ChessBase database
 * TODO: Embedded media (in the .html folder) is not handled
 */
public class TextContentsModel {
    private static final Logger log = LoggerFactory.getLogger(TextContentsModel.class);

    private final static TextLanguage DEFAULT_LANGUAGE = TextLanguage.ENGLISH;

    private final int format;
    private final int unknown;
    @NonNull
    private final Map<TextLanguage, String> titles;
    // Maybe this should be ByteBuffer instead, and allow the rendered to interpret the encoding
    @NonNull
    private final Map<TextLanguage, String> contents;
    @NonNull
    private final Map<TextLanguage, byte[]> formatting;

    public TextContentsModel() {
        this.titles = new HashMap<>();
        this.contents = new HashMap<>();
        this.format = 3;
        this.formatting = new HashMap<>();
        this.unknown = 1;
    }

    public TextContentsModel(int format,
                             Map<TextLanguage, String> titles,
                             Map<TextLanguage, String> contents,
                             Map<TextLanguage, byte[]> formatting,
                             int unknown) {
        this.format = format;  // 1 (old) or 3 (new)
        this.titles = titles;
        this.contents = contents;
        this.formatting = formatting;  // Only if format = 1
        this.unknown = unknown;
    }

    public static TextContentsModel deserialize(int gameId, ByteBuffer buf) throws ChessBaseMoveDecodingException {
        try {
            int size = ByteBufferUtil.getIntB(buf);
            if ((size & (1 << 31)) == 0) {
                log.warn("Most significant bit in size in blob in text should be set");
            }
            size &= ~(1 << 31);

            int textFormat = ByteBufferUtil.getSignedShortL(buf);
            if (textFormat != 1 && textFormat != 2 && textFormat != 3) {
                log.warn("Unknown text format " + textFormat + " in text with id " + gameId);
            }

            HashMap<TextLanguage, String> titles = new HashMap<>();
            int numTitles = ByteBufferUtil.getSignedShortL(buf);
            for (int i = 0; i < numTitles; i++) {
                int languageId = ByteBufferUtil.getSignedShortL(buf);
                int titleLength = ByteBufferUtil.getUnsignedShortL(buf);
                String title = ByteBufferUtil.getFixedSizeByteString(buf, titleLength);
                titles.put(TextLanguage.values()[languageId], title);
            }

            int unknown = ByteBufferUtil.getSignedByte(buf);
            if ((unknown != 0 && unknown != 1) || (unknown == 0 && textFormat == 3)) {
                // 0 and 1 seen for text format 1
                // 1 seen for text format 3
                // Log if other combinations found
                log.warn("Unknown value was " + unknown + " in text with id " + gameId + " with format " + textFormat);
            }

            HashMap<TextLanguage, String> contents = new HashMap<>();
            HashMap<TextLanguage, byte[]> formatting = new HashMap<>();

            if (textFormat == 1 || textFormat == 2) {
                int numTxt = ByteBufferUtil.getSignedShortL(buf);
                for (int i = 0; i < numTxt; i++) {
                    int languageId = ByteBufferUtil.getSignedShortL(buf);
                    int txtLength = textFormat == 1 ?
                            ByteBufferUtil.getUnsignedShortL(buf) :
                            ByteBufferUtil.getIntL(buf);
                    String txt = ByteBufferUtil.getFixedSizeByteString(buf, txtLength);
                    contents.put(TextLanguage.values()[languageId], txt);

                    int formattingSize = textFormat == 1 ?
                            ByteBufferUtil.getUnsignedShortL(buf) :
                            ByteBufferUtil.getIntL(buf);
                    byte[] format = new byte[formattingSize];
                    buf.get(format, 0, formattingSize);
                    formatting.put(TextLanguage.values()[languageId], format);
                }
            }
            if (textFormat == 3) {
                int numHtml = ByteBufferUtil.getSignedShortL(buf);
                for (int i = 0; i < numHtml; i++) {
                    int languageId = ByteBufferUtil.getSignedShortL(buf);
                    int htmlLength = ByteBufferUtil.getIntL(buf);
                    String htmlText = ByteBufferUtil.getFixedSizeByteString(buf, htmlLength);
                    contents.put(TextLanguage.values()[languageId], htmlText);
                    int unknownInt = ByteBufferUtil.getIntL(buf);
                    if (unknownInt != 0) {
                        log.warn("Unknown trailing value " + unknownInt + " in text with id " + gameId);
                    }
                }
            }

            if (buf.position() != size) {
                log.warn("Size of text blob was " + size + " but was at position " + buf.position() + " after deserialization");
            }

            return new TextContentsModel(textFormat, titles, contents, formatting, unknown);
        } catch (BufferUnderflowException e) {
            log.warn("Move data ended abruptly in text " + gameId + ".");
            throw new ChessBaseMoveDecodingException("Moves data header ended abruptly in text " + gameId, e);
        }
    }

    public static String deserializeTitle(int gameId, ByteBuffer buf) {
        // Quick deserialize the first available title
        buf.position(buf.position() + 10);
        int titleLength = ByteBufferUtil.getSignedShortL(buf);
        return ByteBufferUtil.getFixedSizeByteString(buf, titleLength);
    }

    public String getTitle() {
        return getTitle(DEFAULT_LANGUAGE);
    }

    public String getTitle(@NonNull TextLanguage language) {
        String title = titles.get(language);
        if (title == null) {
            if (titles.size() == 0) {
                title = "Untitled";
            } else {
                title = titles.values().stream().findFirst().get();
            }
        }
        return title;
    }

    public void setTitle(@NonNull String title) {
        setTitle(DEFAULT_LANGUAGE, title);
    }

    public void setTitle(@NonNull TextLanguage language, @NonNull String title) {
        this.titles.put(language, title);
    }

    public String getContents() {
        return getContents(DEFAULT_LANGUAGE);
    }

    public String getContents(@NonNull TextLanguage language) {
        String html = this.contents.get(language);
        if (html == null) {
            if (this.contents.size() == 0) {
                html = "Untitled";
            } else {
                html = this.contents.values().stream().findFirst().get();
            }
        }
        return html;
    }

    public void setContents(@NonNull String contents) {
        setContents(DEFAULT_LANGUAGE, contents);
    }

    public void setContents(@NonNull TextLanguage language, @NonNull String html) {
        this.contents.put(language, html);
    }

    public ByteBuffer serialize() {
        if (format != 1 && format != 2 && format != 3) {
            throw new IllegalStateException("Can't serialize text format " + format);
        }

        int size = 11;

        for (TextLanguage language : TextLanguage.values()) {
            if (titles.containsKey(language)) {
                size += 4 + titles.get(language).length();
            }
            if (contents.containsKey(language)) {
                if (format == 1) {
                    size += 4 + contents.get(language).length();
                    size += 2 + formatting.get(language).length;
                } else if (format == 2) {
                    size += 6 + contents.get(language).length();
                    size += 4 + formatting.get(language).length;
                } else if (format == 3) {
                    size += 10 + contents.get(language).length();
                }
            }
        }

        ByteBuffer buf = ByteBuffer.allocate(size);
        ByteBufferUtil.putIntB(buf, size + (1<<31));
        ByteBufferUtil.putShortL(buf, format);

        ByteBufferUtil.putShortL(buf, titles.size());
        for (TextLanguage language : TextLanguage.values()) {
            if (titles.containsKey(language)) {
                String title = titles.get(language);
                ByteBufferUtil.putShortL(buf, language.ordinal());
                ByteBufferUtil.putShortL(buf, title.length());
                ByteBufferUtil.putRawByteString(buf, title);
            }
        }

        ByteBufferUtil.putByte(buf, unknown);
        ByteBufferUtil.putShortL(buf, contents.size());

        if (format == 1 || format == 2) {
            for (TextLanguage language : TextLanguage.values()) {
                if (contents.containsKey(language)) {
                    String content = contents.get(language);
                    ByteBufferUtil.putShortL(buf, language.ordinal());
                    if (format == 1) {
                        ByteBufferUtil.putShortL(buf, content.length());
                    } else {
                        ByteBufferUtil.putIntL(buf, content.length());
                    }
                    ByteBufferUtil.putRawByteString(buf, content);

                    byte[] contentFormat = formatting.get(language);
                    if (format == 1) {
                        ByteBufferUtil.putShortL(buf, contentFormat.length);
                    } else {
                        ByteBufferUtil.putIntL(buf, contentFormat.length);
                    }
                    buf.put(contentFormat);
                }
            }
        }
        if (format == 3) {
            for (TextLanguage language : TextLanguage.values()) {
                if (contents.containsKey(language)) {
                    String content = contents.get(language);
                    ByteBufferUtil.putShortL(buf, language.ordinal());
                    ByteBufferUtil.putIntL(buf, content.length());
                    ByteBufferUtil.putRawByteString(buf, content);
                    ByteBufferUtil.putIntL(buf, 0);
                }
            }
        }
        if (buf.position() != size) {
            log.warn(String.format("Serialized size of text model doesn't match expected size (%d != %d)",
                    size, buf.position()));
        }
        buf.flip();
        return buf;
    }
}
