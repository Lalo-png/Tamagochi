package com.cesba.retropet.data;
import androidx.room.*;
import java.util.List;
@Dao
public interface GameDao {
    @Insert long insertUser(UserEntity u);
    @Query("SELECT * FROM users WHERE username = :u LIMIT 1") UserEntity findUser(String u);

    @Insert(onConflict = OnConflictStrategy.REPLACE) void savePet(PetEntity p);
    @Query("SELECT * FROM pet WHERE userId = :id") PetEntity getPet(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE) void saveInv(InventoryEntity e);
    @Query("SELECT * FROM inventory WHERE userId = :u AND itemId = :i") InventoryEntity getInv(long u, String i);
    @Query("SELECT * FROM inventory WHERE userId = :u AND (quantity > 0 OR equipped = 1)") List<InventoryEntity> getInventory(long u);
    @Query("UPDATE inventory SET equipped = 0 WHERE userId = :u AND slot = :s") void unequipSlot(long u, String s);

    @Insert(onConflict = OnConflictStrategy.REPLACE) long saveMission(MissionEntity m);
    @Query("SELECT * FROM missions WHERE userId = :u") List<MissionEntity> getMissions(long u);
    @Query("SELECT * FROM missions WHERE id = :id") MissionEntity getMission(long id);

    @Query("DELETE FROM pet WHERE userId = :u") void deletePet(long u);
    @Query("DELETE FROM inventory WHERE userId = :u") void deleteInventory(long u);
    @Query("DELETE FROM missions WHERE userId = :u") void deleteMissions(long u);
}
