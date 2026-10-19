import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;

/** Opens only a recovery copy inside the QA app's cache; never the ordinary app. */
public final class RecoveryProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("One recovery directory required");
        File root = new File(args[0]).getCanonicalFile();
        String path = root.getPath();
        if (!path.matches("/data/(user/0|data)/com\\.dishnest\\.app\\.qa/cache/recovery-[a-f0-9]{32}")) {
            throw new IllegalArgumentException("Refusing a non-recovery directory");
        }
        File database = new File(root, "databases/dishnest.db");
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getPath(), null,
                SQLiteDatabase.OPEN_READWRITE)) {
            try (Cursor check = db.rawQuery("PRAGMA integrity_check", null)) {
                if (!check.moveToFirst() || !"ok".equals(check.getString(0)) || check.moveToNext()) {
                    throw new IllegalStateException("Restored SQLite integrity check failed");
                }
            }
            System.out.println("RECOVERY_SQLITE_OK version=" + db.getVersion());
        }
    }
}
