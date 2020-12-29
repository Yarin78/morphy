package se.yarin.asflib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * The @{@link ASFScriptCommandReader} reads script commands from an ASF container, e.g. wmv
 */
public class ASFScriptCommandReader {
    private static final Logger log = LoggerFactory.getLogger(ASFScriptCommandReader.class);

    private static final int ucHeaderObjectSig [] = {
            0x30, 0x26, 0xb2, 0x75, 0x8e, 0x66, 0xcf, 0x11,
            0xa6, 0xd9, 0x00, 0xaa, 0x00, 0x62, 0xce, 0x6c
    };

    private static final int ucScriptCommandObjectSig [] = {
            0x30, 0x1a, 0xfb, 0x1e, 0x62, 0x0b, 0xd0, 0x11,
            0xa3, 0x9b, 0x00, 0xa0, 0xc9, 0x03, 0x48, 0xf6
    };

    private final InputStream inputStream;
    private String[] commandTypes;
    private LittleEndianDataInputStream pFile;
    private boolean init = false;
    private int noScriptCommands, curScriptCommand;

    public ASFScriptCommandReader(File file) throws FileNotFoundException {
        this.inputStream = new FileInputStream(file);
    }

    public ASFScriptCommandReader(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    /**
     * Validates that the stream contains data in the ASF container format,
     * and that there is a header object with script commands
     * Is called automatically by {@link #read()} if not done so explicitly.
     * May only be called once.
     * @return the number of script commands
     * @throws ASFException if the input stream is not a valid ASF file
     * @throws IOException if there were an error reading the input,
     * or if init has been called more than once
     */
    public int init() throws IOException {
        if (init) {
            throw new IOException("Init can only be called once");
        }

        log.debug("Initializing ASF stream");

        pFile = new LittleEndianDataInputStream(new BufferedInputStream(inputStream));

        // Validate header signature
        for (int i = 0; i < ucHeaderObjectSig.length; i++) {
            if (pFile.readUnsignedByte() != ucHeaderObjectSig[i]) {
                throw new ASFException("Not an ASF file");
            }
        }

        long headerLength = pFile.readLong();
        int objectsInHeader = pFile.readInt();
        pFile.skip(2);

        log.debug("ASF header length " + headerLength);
        log.debug("ASF objects in header " + objectsInHeader);

        noScriptCommands = 0;
        curScriptCommand = 0;
        for (int objNo = 0; objNo < objectsInHeader; objNo++) {
            boolean isScriptCommand = true;
            for (int i = 0; i < ucScriptCommandObjectSig.length; i++) {
                if (pFile.readUnsignedByte() != ucScriptCommandObjectSig[i]) {
                    isScriptCommand = false;
                }
            }
            long objLength = pFile.readLong();
            if (isScriptCommand) {
                log.debug("Found script command block with length " + objLength);
                pFile.skip(16);
                noScriptCommands = pFile.readUnsignedShort();

                int wTypes = pFile.readUnsignedShort();
                commandTypes = new String[wTypes];
                for (int i = 0; i < wTypes; i++) {
                    commandTypes[i] = readString(pFile);
                }
                // Don't search further
                break;
            } else {
                pFile.skip(objLength - 24);
            }
        }

        init = true;

        log.debug(noScriptCommands + " script commands found");

        return noScriptCommands;
    }

    /**
     * Determines if there are more script commands in the stream that haven't been read yet
     * @return true if there are more commands to be read
     * @throws IOException if there were an error reading from the stream
     */
    public boolean hasMore() throws IOException {
        if (!init) {
            init();
        }

        return curScriptCommand < noScriptCommands;
    }

    /**
     * Gets the next script command from the stream
     * @return an ASF script command
     * @throws EOFException if there are no more script commands
     * @throws ASFException if there were some error in the format of the script command
     * @throws IOException if there were an error reading from the stream
     */
    public ASFScriptCommand read() throws IOException {
        if (!init) {
            init();
        }

        if (!hasMore()) {
            throw new EOFException("There are no more script commands");
        }

        curScriptCommand++;

        int ms = pFile.readInt();
        int type = pFile.readUnsignedShort();
        String command = readString(pFile);
        if (type < 0 || type >= commandTypes.length) {
            throw new ASFException("Invalid command type in script command: " + type);
        }

        return new ASFScriptCommand(ms, commandTypes[type], command);
    }

    private String readString(LittleEndianDataInputStream dis) throws IOException {
        int len = dis.readUnsignedShort();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char c = dis.readChar();
            // If the last character is a 0, don't include it
            if (i == len - 1 && c == 0) break;
            sb.append(c);
        }
        return sb.toString();
    }

    public void close() throws IOException {
        inputStream.close();
    }
}
