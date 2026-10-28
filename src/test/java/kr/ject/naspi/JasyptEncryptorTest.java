package kr.ject.naspi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.junit.jupiter.api.Test;

/**
 * application.yaml 에 넣을 ENC(...) 값을 만드는 용도.
 * KEY / PLAIN 을 채우고 로컬에서 실행한 뒤 출력된 ENC(...) 를 복사할 것.
 * 값이 비어 있으면 건너뛰므로 CI 에서는 항상 통과. 실제 값은 절대 커밋하지 말고 실행 후 다시 비울 것.
 */
class JasyptEncryptorTest {

    private static final String KEY = "";
    private static final String PLAIN = "";

    @Test
    void encrypt() {
        assumeFalse(KEY.isBlank() || PLAIN.isBlank(), "KEY / PLAIN 이 비어 있어 건너뜀");

        StandardPBEStringEncryptor encryptor = createEncryptor(KEY);
        String encrypted = encryptor.encrypt(PLAIN);
        System.out.println("ENC(" + encrypted + ")");

        assertThat(encryptor.decrypt(encrypted)).isEqualTo(PLAIN);
    }

    // application.yaml 의 jasypt.encryptor 설정과 동일하게 유지
    private StandardPBEStringEncryptor createEncryptor(String password) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(password);
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setKeyObtentionIterations(1000);
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setStringOutputType("base64");
        return encryptor;
    }

}
