package yarin.cbhlib;

/**
 * Common base class for data records in the CBH file format,
 * such as yarin.cbhlib.GameHeader, yarin.cbhlib.Player, yarin.cbhlib.Tournament, yarin.cbhlib.Annotator, yarin.cbhlib.Source, yarin.cbhlib.Team
 */
public abstract class DataRecord {
    private int id;
    private Database ownerBase;

    /**
     * @return the id os this data record. If it's new and hasn't been saved to file yet, the id is 0.
     */
    public int getId()
    {
        return id;
    }

    /**
     * @return the ChessBase database where this record is contained in
     */
    public Database getOwnerBase()
    {
        return ownerBase;
    }

    /**
     * Creates a new data record in an opened CBH base. The record will only be created
     * in memory, and not saved to disk until explicitly done so.
     * @param database the database to create the record in
     */
    public DataRecord(Database database)
    {
        if (database == null)
            throw new IllegalArgumentException("database is null");
        ownerBase = database;
        id = 0;
    }

    /**
     * Create a new datarecord from file
     * @param database the database where the record was located in
     * @param id the id of the record
     */
    DataRecord(Database database, int id)
    {
        if (database == null)
            throw new IllegalArgumentException("database is null");
        ownerBase = database;
        this.id = id;
    }
}
