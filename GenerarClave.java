import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerarClave {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode("admin1234"));
    }
}
