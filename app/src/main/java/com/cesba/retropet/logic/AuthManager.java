package com.cesba.retropet.logic;
import com.cesba.retropet.data.*;
import java.security.MessageDigest;
/** Login local (sin internet) para asociar la partida a un usuario. Llamar desde hilo secundario. */
public class AuthManager {
    private final GameDao dao;
    public AuthManager(GameDao dao) { this.dao = dao; }
    static String hash(String s) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    /** Devuelve userId (>0) o -1 si el usuario ya existe / datos invalidos. */
    public long register(String user, String pass) {
        if (user == null || user.trim().length() < 3 || pass == null || pass.length() < 4) return -1;
        if (dao.findUser(user.trim()) != null) return -1;
        UserEntity u = new UserEntity(); u.username = user.trim(); u.passHash = hash(pass);
        return dao.insertUser(u);
    }
    /** Devuelve userId o -1 si credenciales incorrectas. */
    public long login(String user, String pass) {
        UserEntity u = dao.findUser(user == null ? "" : user.trim());
        return (u != null && u.passHash.equals(hash(pass))) ? u.id : -1;
    }
}
