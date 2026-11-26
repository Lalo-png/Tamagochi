package com.cesba.retropet.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "inventory", primaryKeys = {"userId", "itemId"})
public class InventoryEntity {
    public long userId;
    @NonNull public String itemId = "";
    public int quantity;
    public String slot;        // WALL, CEILING, FLOOR (solo decoracion)
    public boolean equipped;
}
