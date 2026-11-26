package com.cesba.retropet.data;
import androidx.room.*;
@Entity(tableName = "users", indices = {@Index(value = "username", unique = true)})
public class UserEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public String username;
    public String passHash;
}
