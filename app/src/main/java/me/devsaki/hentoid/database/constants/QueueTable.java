package me.devsaki.hentoid.database.constants;

/**
 * Created by Robb_w on 2018/04
 * db Queue table
 */
public abstract class QueueTable {

    public static final String TABLE_NAME = "queue";

    public static final String INSERT_STATEMENT = "INSERT OR REPLACE INTO " + TABLE_NAME + " VALUES (?,?);";

    // COLUMN NAMES
    private static final String ID_COLUMN = "content_id";
    private static final String ORDER_COLUMN = "rank";

    // CREATE
    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "("
            + ID_COLUMN + " INTEGER PRIMARY KEY, " + ORDER_COLUMN + " INTEGER DEFAULT 0)";

    // DELETE
    public static final String DELETE_STATEMENT = "DELETE FROM " + TABLE_NAME + " WHERE " + ID_COLUMN + " = ?";

    // UPDATE
    public static final String UPDATE_STATEMENT = "UPDATE "
            + TABLE_NAME + " SET " + ORDER_COLUMN + " = ? WHERE " + ID_COLUMN + " = ?";

    // SELECT
    public static final String SELECT_QUEUE = "SELECT * FROM " + TABLE_NAME + " C ORDER BY " + ORDER_COLUMN + " ASC";
}
