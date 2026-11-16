package com.socialhub.platform.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Ningún secreto sobrevive al filtro, el texto normal no se toca y la expresión de application.yml es la misma. */
class SecretRedactorTest {

    @Test
    void tapaCadaSecretoYConservaElNombreDelCampo() {
        for (Secrets.Sample s : Secrets.SAMPLES) {
            String out = SecretRedactor.redact(s.text());
            assertThat(out).as(s.text()).doesNotContain(s.secret()).contains("***");
        }
        assertThat(SecretRedactor.redact("password=hunter2 usuario=raul")).isEqualTo("password=*** usuario=raul");
        assertThat(SecretRedactor.redact("Authorization: Bearer abc123.def")).isEqualTo("Authorization: Bearer ***");
    }

    @Test
    void noTocaElTextoNormal() {
        for (String text : Secrets.HARMLESS) assertThat(SecretRedactor.redact(text)).isEqualTo(text);
    }

    @Test
    void laExpresionDeApplicationYmlEsLaMisma() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties p = yaml.getObject();
        assertThat(p.getProperty("socialhub.logging.redaction-regex")).isEqualTo(SecretRedactor.REGEX);
        assertThat(p.getProperty("socialhub.logging.redaction-replacement")).isEqualTo(SecretRedactor.REPLACEMENT);
    }
}
