package game.core;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SecretNumberGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int length){
        if (length < 1 || length > 10){
            throw new IllegalArgumentException("Length must be between 1 and 10");
        }

        List<Integer> digits = new ArrayList<>();
        for (int i = 0; i <= 9; i++) digits.add(i);
        Collections.shuffle(digits, RANDOM);

        List<Integer> chosen = new ArrayList<>(digits.subList(0, length));
        if (chosen.get(0) == 0 && length > 1) {
            for (int i = 1; i < chosen.size(); i++) {
                if (chosen.get(i) != 0) {
                    Collections.swap(chosen, 0, i);
                    break;
                }
            }
        }
        StringBuilder sb = new StringBuilder(length);
        for (int d : chosen) sb.append(d);
        return sb.toString();
    }
}
