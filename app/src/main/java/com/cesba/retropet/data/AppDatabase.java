package com.cesba.retropet.data;
import android.content.Context;
import androidx.room.*;
@Database(entities = {UserEntity.class, PetEntity.class, InventoryEntity.class, MissionEntity.class},
          version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract GameDao dao();
    private static AppDatabase INSTANCE;
    public static synchronized AppDatabase get(Context c) {
        if (INSTANCE == null)
            INSTANCE = Room.databaseBuilder(c.getApplicationContext(), AppDatabase.class, "retropet.db").build();
        return INSTANCE;
    }
}
