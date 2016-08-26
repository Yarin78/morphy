package se.yarin.cbhlib;

import se.yarin.cbhlib.entities.EntityStatsValidator;
import se.yarin.cbhlib.entities.EntityStorageException;

import java.io.File;
import java.io.IOException;

public class VerifyEntityStats {

    public static void main(String[] args) throws IOException, EntityStorageException {
        long start = System.currentTimeMillis();
//        Database db = Database.open(new File("testbases/Jimmys bases/jimmy.cbh"));
        Database db = Database.open(new File("testbases/Mega Database 2016/Mega Database 2016.cbh"));
//        Database db = Database.open(new File("testbases/tmp/Entity Test/entity_stats_test.cbh"));
        try {
            EntityStatsValidator validator = new EntityStatsValidator(db);
            validator.validateEntityStatistics(false);
        } finally {
            db.close();
        }
        System.out.println("Done in " + (System.currentTimeMillis() - start) + " ms");
    }
}
