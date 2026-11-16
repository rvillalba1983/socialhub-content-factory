package com.socialhub.publishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * «Conectar con Instagram» de extremo a extremo contra un Meta de mentira (servidor HTTP local; nunca se llama a Meta de
 * verdad ni se usa ninguna clave real): App ID y App Secret, «state» aleatorio y de un solo uso, intercambio del código,
 * clave de larga duración con su caducidad, autodetección de la cuenta de cada marca, aviso de caducidad, renovación y
 * convivencia con la clave de usuario del sistema.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.engine.poll-millis=200",
        "socialhub.engine.initial-delay-millis=200",
        "socialhub.ai.initial-delay-millis=300",
        "socialhub.ai.dispatch-millis=300",
        "socialhub.ai.read-millis=300",
        "socialhub.ai.auto-initial-delay-millis=3600000",
        "socialhub.publishing.initial-delay-millis=3600000"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InstagramOAuthTest {

    // Todo lo de aquí es inventado: ninguna clave, App ID ni identificador es real
    static final String APP_ID = "1234567890123";
    static final String SECRET = "s3cr3tDePruebaQueNoExiste0123456789";
    static final String SHORT = "EAAB.corta-de-prueba-0001";
    static final String LONG = "EAAB.larga-de-prueba-0002";
    static final String SYSTEM_KEY = "EAAB.sistema-de-prueba-0003";
    static final String CODE = "codigo-de-prueba-ok";
    static final String IG_DELITO = "17841400000000001";
    static final String IG_LITOQUES = "17841400000000002";
    static final String IG_OTHER = "17841400000000003";
    static final String REDIRECT = "http://localhost:8686/api/instagram/oauth/callback";
    static final long LONG_SECONDS = 5_184_000;
    static final PublishingEngineTest.MovableClock CLOCK = new PublishingEngineTest.MovableClock();

    static Path dataDir;
    static HttpServer meta;
    static String base;

    /** Lo que ha recibido el Meta de mentira: «MÉTODO ruta» y sus cuerpos (ya decodificados). */
    static final List<String> calls = new CopyOnWriteArrayList<>();
    static final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    static final Map<String, String> authorization = new ConcurrentHashMap<>();
    static final List<String> everything = new CopyOnWriteArrayList<>();

    @Autowired MockMvc mvc;
    @Autowired InstagramOAuthService oauth;
    private final JsonMapper json = JsonMapper.builder().build();

    @TestConfiguration
    static class Config {
        @Bean @Primary Clock movableClock() { return CLOCK; }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        dataDir = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "it-instagram-oauth");
        registry.add("socialhub.data-dir", dataDir::toString);
        meta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        meta.createContext("/", InstagramOAuthTest::handle);
        meta.start();
        base = "http://127.0.0.1:" + meta.getAddress().getPort();
        registry.add("socialhub.instagram.graph-url", () -> base);
        registry.add("socialhub.instagram.dialog-url", () -> base + "/dialogo");
        registry.add("server.port", () -> "8686");
    }

    @AfterAll
    static void stop() { meta.stop(0); }

    static Map<String, String> form(String body) {
        if (body.isBlank()) return Map.of();
        return Arrays.stream(body.split("&")).map(p -> p.split("=", 2)).collect(
                Collectors.toMap(p -> URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                        p -> p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "", (a, b) -> b));
    }

    static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String call = ex.getRequestMethod() + " " + path;
        calls.add(call);
        everything.add(call + "?" + ex.getRequestURI().getRawQuery() + " " + body);
        authorization.put(call, String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
        int code = 200;
        String reply;
        if (path.equals("/v23.0/oauth/access_token")) {
            Map<String, String> f = form(body);
            tokenRequests.add(f);
            if (!APP_ID.equals(f.get("client_id")) || !SECRET.equals(f.get("client_secret"))) {
                code = 400;
                reply = "{\"error\":{\"message\":\"Error validating client secret.\",\"type\":\"OAuthException\",\"code\":1}}";
            } else if ("client_credentials".equals(f.get("grant_type"))) {
                reply = "{\"access_token\":\"" + APP_ID + "|app-token\",\"token_type\":\"bearer\"}";
            } else if ("fb_exchange_token".equals(f.get("grant_type"))) {
                reply = SHORT.equals(f.get("fb_exchange_token"))
                        ? "{\"access_token\":\"" + LONG + "\",\"token_type\":\"bearer\",\"expires_in\":" + LONG_SECONDS + "}"
                        : "{\"error\":{\"message\":\"Invalid OAuth access token.\",\"code\":190}}";
                if (!SHORT.equals(f.get("fb_exchange_token"))) code = 400;
            } else if (CODE.equals(f.get("code")) && REDIRECT.equals(f.get("redirect_uri"))) {
                reply = "{\"access_token\":\"" + SHORT + "\",\"token_type\":\"bearer\",\"expires_in\":3600}";
            } else {
                code = 400;
                reply = "{\"error\":{\"message\":\"This authorization code has been used.\",\"code\":100,\"error_subcode\":36009}}";
            }
        } else if (!("OAuth " + LONG).equals(authorization.get(call)) && !("OAuth " + SYSTEM_KEY).equals(authorization.get(call))) {
            code = 401;
            reply = "{\"error\":{\"message\":\"Invalid OAuth access token\",\"code\":190}}";
        } else if (path.equals("/v23.0/me")) {
            reply = "{\"id\":\"999\",\"name\":\"Raúl de prueba\"}";
        } else if (path.equals("/v23.0/me/accounts")) {
            reply = "{\"data\":["
                    + "{\"name\":\"Página El DeLito\",\"id\":\"p1\",\"instagram_business_account\":{\"id\":\"" + IG_DELITO + "\",\"username\":\"Elde_Lito\"}},"
                    + "{\"name\":\"Página Litoques\",\"id\":\"p2\",\"instagram_business_account\":{\"id\":\"" + IG_LITOQUES + "\",\"username\":\"litoques\"}},"
                    + "{\"name\":\"Página sin Instagram\",\"id\":\"p3\"},"
                    + "{\"name\":\"Página de otro\",\"id\":\"p4\",\"instagram_business_account\":{\"id\":\"" + IG_OTHER + "\",\"username\":\"otra_cuenta\"}}"
                    + "],\"paging\":{\"cursors\":{\"before\":\"a\",\"after\":\"b\"}}}";
        } else if (path.endsWith("/content_publishing_limit")) {
            reply = "{\"data\":[{\"quota_usage\":1,\"config\":{\"quota_total\":100}}]}";
        } else if (path.equals("/v23.0/" + IG_DELITO) || path.equals("/v23.0/" + IG_LITOQUES)) {
            reply = "{\"id\":\"x\",\"username\":\"x\"}";
        } else {
            code = 400;
            reply = "{\"error\":{\"message\":\"Unsupported get request\",\"code\":100}}";
        }
        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @BeforeEach
    void reset() {
        calls.clear();
        tokenRequests.clear();
        everything.clear();
    }

    // ---------------------------------------------------------------- App ID y App Secret

    @Test
    @Order(1)
    void theAppIsCheckedWithMetaAndTheSecretIsKeptOnlyInTheDataFolder() throws Exception {
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}");
        write(post("/api/brands"), "{\"name\":\"Litoques\",\"handle\":\"litoques\",\"color\":\"#2E7DD2\"}");
        write(post("/api/brands"), "{\"name\":\"Sin coincidencia\",\"handle\":\"@nadie\",\"color\":\"#777777\"}");

        // Sin App ID ni Secret no se puede empezar
        write(post("/api/instagram/oauth/start"), "{}").andExpect(status().isBadRequest());
        JsonNode empty = get("/api/instagram");
        assertThat(empty.get("configured").asBoolean()).isFalse();
        assertThat(empty.get("source").asString()).isEqualTo("NONE");
        assertThat(empty.get("app_secret_saved").asBoolean()).isFalse();
        // La dirección que hay que dar a Meta sale en pantalla, exacta
        assertThat(empty.get("redirect_uri").asString()).isEqualTo(REDIRECT);

        // Mal formado, o con un secreto que Meta no acepta: no se guarda nada
        write(put("/api/instagram/app"), "{\"app_id\":\"abc\",\"app_secret\":\"" + SECRET + "\"}").andExpect(status().isBadRequest());
        String wrong = write(put("/api/instagram/app"), "{\"app_id\":\"" + APP_ID + "\",\"app_secret\":\"OtroSecretoQueNoVale0123456789\"}")
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(wrong).contains("App Secret").doesNotContain("OtroSecretoQueNoVale").doesNotContain("Error validating");
        assertThat(dataDir.resolve("secretos/instagram-app-secret.key")).doesNotExist();
        // Sin secreto guardado, tampoco vale dejarlo vacío
        write(put("/api/instagram/app"), "{\"app_id\":\"" + APP_ID + "\"}").andExpect(status().isBadRequest());

        String saved = write(put("/api/instagram/app"), "{\"app_id\":\"" + APP_ID + "\",\"app_secret\":\"" + SECRET + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode status = json.readTree(saved);
        assertThat(status.get("app_id").asString()).isEqualTo(APP_ID);
        assertThat(status.get("app_secret_saved").asBoolean()).isTrue();
        // El secreto no vuelve a salir por la API
        assertThat(saved).doesNotContain(SECRET);
        assertThat(get("/api/instagram").toString()).doesNotContain(SECRET);
        assertThat(Files.readString(dataDir.resolve("secretos/instagram-app-secret.key"))).isEqualTo(SECRET);
        // La comprobación con Meta lleva el secreto en el cuerpo del POST, nunca en la dirección
        assertThat(everything).noneMatch(c -> c.split(" ", 2)[0].contains(SECRET));

        // Cambiar solo el App ID conserva el secreto
        write(put("/api/instagram/app"), "{\"app_id\":\"" + APP_ID + "\",\"app_secret\":\"\"}").andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- state y vuelta de Meta

    @Test
    @Order(2)
    void startBuildsMetaUrlWithAFreshRandomState() throws Exception {
        URI first = URI.create(start());
        URI second = URI.create(start());
        assertThat(first.toString()).startsWith(base + "/dialogo/v23.0/dialog/oauth?");
        Map<String, String> q = form(first.getRawQuery());
        assertThat(q.get("client_id")).isEqualTo(APP_ID);
        assertThat(q.get("redirect_uri")).isEqualTo(REDIRECT);
        assertThat(q.get("response_type")).isEqualTo("code");
        // Permisos mínimos
        assertThat(q.get("scope").split(",")).containsExactlyInAnyOrder("instagram_basic", "instagram_content_publish",
                "pages_show_list", "pages_read_engagement");
        // state aleatorio, largo y distinto cada vez; el secreto nunca va en la dirección
        assertThat(q.get("state")).hasSizeGreaterThanOrEqualTo(40).isNotEqualTo(form(second.getRawQuery()).get("state"));
        assertThat(first.toString()).doesNotContain(SECRET);
        assertThat(calls).isEmpty();
    }

    @Test
    @Order(3)
    void aForgedOrUnknownStateIsRejectedWithoutCallingMeta() throws Exception {
        String message = callbackError("/api/instagram/oauth/callback?code=" + CODE + "&state=inventado");
        assertThat(message).contains("no es de una conexión que hayas empezado");
        assertThat(callbackError("/api/instagram/oauth/callback?code=" + CODE)).contains("no es de una conexión");
        assertThat(callbackError("/api/instagram/oauth/callback")).contains("no es de una conexión");
        // Ni un intercambio de código ni nada con Meta
        assertThat(calls).isEmpty();
        assertThat(get("/api/instagram").path("connection").isNull() || get("/api/instagram").path("connection").isMissingNode()).isTrue();
    }

    @Test
    @Order(4)
    void anExpiredStateIsRejectedToo() throws Exception {
        String state = form(URI.create(start()).getRawQuery()).get("state");
        CLOCK.set(Instant.now().plus(Duration.ofMinutes(11)));
        try {
            assertThat(callbackError("/api/instagram/oauth/callback?code=" + CODE + "&state=" + state)).contains("más de 10 minutos");
            assertThat(calls).isEmpty();
        } finally {
            CLOCK.reset();
        }
    }

    @Test
    @Order(5)
    void deniedPermissionAndWrongCodeAreExplainedAndChangeNothing() throws Exception {
        String denied = form(URI.create(start()).getRawQuery()).get("state");
        assertThat(callbackError("/api/instagram/oauth/callback?error=access_denied&error_description=x&state=" + denied))
                .contains("No has dado el permiso");
        assertThat(calls).isEmpty();
        // El state se gastó aunque Meta trajera un error
        assertThat(callbackError("/api/instagram/oauth/callback?code=" + CODE + "&state=" + denied)).contains("no es de una conexión");

        String state = form(URI.create(start()).getRawQuery()).get("state");
        assertThat(callbackError("/api/instagram/oauth/callback?code=malo&state=" + state)).contains("código de autorización");
        assertThat(get("/api/instagram").path("connection").isNull() || get("/api/instagram").path("connection").isMissingNode()).isTrue();
        assertThat(dataDir.resolve("secretos/instagram-oauth.key")).doesNotExist();
    }

    @Test
    @Order(6)
    void theFullFlowExchangesTheCodeGetsALongLivedKeyAndDetectsEachBrandsAccount(CapturedOutput output) throws Exception {
        Instant before = Instant.now();
        String state = form(URI.create(start()).getRawQuery()).get("state");
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/instagram/oauth/callback?code=" + CODE + "&state=" + state))
                .andExpect(status().isFound()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).startsWith("/?instagram=ok&message=");
        String message = URLDecoder.decode(location.substring(location.indexOf("message=") + 8), StandardCharsets.UTF_8);
        assertThat(message).contains("@Elde_Lito", "@litoques", "@otra_cuenta").contains("El DeLito → @Elde_Lito", "Litoques → @litoques");

        // Intercambio del código y paso a larga duración, con el secreto en el cuerpo y sin él en la dirección
        assertThat(tokenRequests).hasSize(2);
        assertThat(tokenRequests.get(0)).containsEntry("code", CODE).containsEntry("redirect_uri", REDIRECT).containsEntry("client_id", APP_ID);
        assertThat(tokenRequests.get(1)).containsEntry("grant_type", "fb_exchange_token").containsEntry("fb_exchange_token", SHORT);
        assertThat(everything).noneMatch(c -> c.split(" ", 2)[0].contains(SECRET) || c.split(" ", 2)[0].contains(SHORT));
        // Lista las páginas con su cuenta de Instagram, con la clave de larga duración en la cabecera
        assertThat(calls).contains("GET /v23.0/me/accounts");
        assertThat(authorization.get("GET /v23.0/me/accounts")).isEqualTo("OAuth " + LONG);
        assertThat(everything).anyMatch(c -> c.startsWith("GET /v23.0/me/accounts?") && c.contains("instagram_business_account"));

        // Estado: conectado, quién, qué cuentas y cuándo caduca (60 días desde ahora)
        JsonNode status = get("/api/instagram");
        assertThat(status.get("configured").asBoolean()).isTrue();
        assertThat(status.get("source").asString()).isEqualTo("OAUTH");
        JsonNode connection = status.get("connection");
        assertThat(connection.get("name").asString()).isEqualTo("Raúl de prueba");
        assertThat(connection.get("usernames")).extracting(JsonNode::asString).containsExactly("Elde_Lito", "litoques", "otra_cuenta");
        Instant expires = Instant.parse(connection.get("expires_at").asString());
        assertThat(expires).isBetween(before.plusSeconds(LONG_SECONDS - 30), Instant.now().plusSeconds(LONG_SECONDS + 30));
        assertThat(connection.get("days_left").asInt()).isEqualTo(59);
        assertThat(connection.get("expired").asBoolean()).isFalse();
        // Nunca sale ninguna clave por la API
        assertThat(status.toString()).doesNotContain(LONG).doesNotContain(SHORT).doesNotContain(SECRET);

        // Autodetección por @usuario (sin distinguir mayúsculas ni la @): cada marca recibe su cuenta; la de otro nombre, no
        assertThat(get("/api/brands/el-delito/instagram-account").get("ig_user_id").asString()).isEqualTo(IG_DELITO);
        assertThat(get("/api/brands/el-delito/instagram-account").get("username").asString()).isEqualTo("Elde_Lito");
        assertThat(get("/api/brands/litoques/instagram-account").get("ig_user_id").asString()).isEqualTo(IG_LITOQUES);
        assertThat(get("/api/brands/sin-coincidencia/instagram-account").path("ig_user_id").isNull() || get("/api/brands/sin-coincidencia/instagram-account").path("ig_user_id").isMissingNode()).isTrue();

        // La clave vive solo en la carpeta de datos; ni en los registros ni en lo que devuelve la API
        assertThat(Files.readString(dataDir.resolve("secretos/instagram-oauth.key"))).contains(LONG);
        assertThat(output.getAll()).doesNotContain(LONG).doesNotContain(SHORT).doesNotContain(SECRET);

        // El mismo state no sirve dos veces (repetir la vuelta no cambia nada)
        calls.clear();
        assertThat(callbackError("/api/instagram/oauth/callback?code=" + CODE + "&state=" + state)).contains("no es de una conexión");
        assertThat(calls).isEmpty();
    }

    @Test
    @Order(7)
    void detectedAccountsShowWhichBrandHasEach() throws Exception {
        JsonNode items = get("/api/instagram/accounts").get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("username").asString()).isEqualTo("Elde_Lito");
        assertThat(items.get(0).get("page_name").asString()).isEqualTo("Página El DeLito");
        assertThat(items.get(0).get("brand_id").asString()).isEqualTo("el-delito");
        assertThat(items.get(2).get("username").asString()).isEqualTo("otra_cuenta");
        assertThat(items.get(2).path("brand_id").isNull()).isTrue();
        assertThat(authorization.get("GET /v23.0/me/accounts")).isEqualTo("OAuth " + LONG);
    }

    @Test
    @Order(8)
    void theConnectionKeyIsTheOneUsedToCallMetaAndItsStateShowsInTheArmory() throws Exception {
        // La comprobación de la API oficial usa la conexión, no hace falta pegar ninguna clave
        JsonNode check = json.readTree(write(post("/api/instagram/check"), "{}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(check.get("name").asString()).isEqualTo("Raúl de prueba");
        assertThat(authorization.get("GET /v23.0/me")).isEqualTo("OAuth " + LONG);
        String note = armoryNote();
        assertThat(note).contains("Conectado con Meta · caduca el ");
        // Asignar a mano otra cuenta sigue funcionando (campo manual)
        JsonNode account = json.readTree(write(put("/api/brands/sin-coincidencia/instagram-account"), "{\"ig_user_id\":\"" + IG_LITOQUES + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(account.get("ig_user_id").asString()).isEqualTo(IG_LITOQUES);
        write(put("/api/brands/sin-coincidencia/instagram-account"), "{\"ig_user_id\":\"\"}").andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- Caducidad, renovación y clave del sistema

    @Test
    @Order(9)
    void sevenDaysOrLessBeforeExpiryThereIsANoticeAndAfterwardsTheKeyStopsWorking() throws Exception {
        assertThat(oauth.notice()).isEmpty();
        assertThat(recommendation()).isNull();

        // Quedan 5 días y una hora: «Qué hacer ahora» avisa y lleva a la tarjeta de Instagram
        CLOCK.set(Instant.now().plusSeconds(LONG_SECONDS).minus(Duration.ofDays(5)).minusSeconds(3600));
        try {
            assertThat(oauth.notice()).isPresent();
            JsonNode r = recommendation();
            assertThat(r).isNotNull();
            assertThat(r.get("title").asString()).isEqualTo("La conexión con Instagram caduca en 5 días");
            assertThat(r.get("target").get("screen").asString()).isEqualTo("INSTAGRAM");
            assertThat(get("/api/instagram").get("connection").get("days_left").asInt()).isEqualTo(5);
            assertThat(get("/api/instagram").get("source").asString()).isEqualTo("OAUTH");

            // Caducada: ya no se usa, la ruta queda sin configurar y se avisa
            CLOCK.set(Instant.now().plusSeconds(LONG_SECONDS + 60));
            JsonNode expired = get("/api/instagram");
            assertThat(expired.get("source").asString()).isEqualTo("NONE");
            assertThat(expired.get("configured").asBoolean()).isFalse();
            assertThat(expired.get("connection").get("expired").asBoolean()).isTrue();
            assertThat(recommendation().get("title").asString()).isEqualTo("La conexión con Instagram ha caducado");
            write(post("/api/instagram/check"), "{}").andExpect(status().isBadRequest());

            // La clave de usuario del sistema es la alternativa: no caduca y la ruta usa la que haya
            write(put("/api/instagram/key"), "{\"key\":\"" + SYSTEM_KEY + "\"}").andExpect(status().isOk());
            JsonNode fallback = get("/api/instagram");
            assertThat(fallback.get("source").asString()).isEqualTo("SYSTEM_USER");
            assertThat(fallback.get("configured").asBoolean()).isTrue();
            assertThat(recommendation().get("detail").asString()).contains("clave de usuario del sistema");
            write(post("/api/instagram/check"), "{}").andExpect(status().isOk());
            assertThat(authorization.get("GET /v23.0/me")).isEqualTo("OAuth " + SYSTEM_KEY);

            // «Renovar conexión»: empezar de nuevo y volver, con un clic; el aviso desaparece y manda la conexión
            CLOCK.set(Instant.now().plusSeconds(LONG_SECONDS + 3600));
            String state = form(URI.create(start()).getRawQuery()).get("state");
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/instagram/oauth/callback?code=" + CODE + "&state=" + state))
                    .andExpect(status().isFound()).andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/?instagram=ok")));
            assertThat(get("/api/instagram").get("source").asString()).isEqualTo("OAUTH");
            assertThat(get("/api/instagram").get("connection").get("days_left").asInt()).isEqualTo(59);
            assertThat(recommendation()).isNull();
            // Las cuentas ya asignadas se conservan
            assertThat(get("/api/brands/el-delito/instagram-account").get("ig_user_id").asString()).isEqualTo(IG_DELITO);
        } finally {
            CLOCK.reset();
        }
    }

    @Test
    @Order(10)
    void disconnectingKeepsTheSystemKeyAndTheBrandAccounts() throws Exception {
        write(delete("/api/instagram/oauth"), "").andExpect(status().isNoContent());
        JsonNode status = get("/api/instagram");
        assertThat(status.path("connection").isNull()).isTrue();
        assertThat(status.get("source").asString()).isEqualTo("SYSTEM_USER");
        assertThat(status.get("app_secret_saved").asBoolean()).isTrue();
        assertThat(dataDir.resolve("secretos/instagram-oauth.key")).doesNotExist();
        assertThat(get("/api/brands/el-delito/instagram-account").get("ig_user_id").asString()).isEqualTo(IG_DELITO);
    }

    // ---------------------------------------------------------------- Ayudas

    private String start() throws Exception {
        return json.readTree(write(post("/api/instagram/oauth/start"), "{}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("authorization_url").asString();
    }

    /** Pide la vuelta de Meta y devuelve el texto del error de la redirección. */
    private String callbackError(String url) throws Exception {
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
                .andExpect(status().isFound()).andReturn().getResponse();
        String location = response.getHeader("Location");
        assertThat(location).startsWith("/?instagram=error&message=");
        return URLDecoder.decode(location.substring(location.indexOf("message=") + 8), StandardCharsets.UTF_8);
    }

    private String armoryNote() throws Exception {
        for (JsonNode item : get("/api/armory-items").get("items")) {
            if ("instagram-api".equals(item.get("id").asString())) return item.get("note").asString();
        }
        throw new AssertionError("«Instagram · API oficial» no está en la armería");
    }

    private JsonNode recommendation() throws Exception {
        for (JsonNode r : get("/api/factory").get("recommendations")) {
            if ("INSTAGRAM_CONNECTION_EXPIRING".equals(r.get("kind").asString())) return r;
        }
        return null;
    }

    private JsonNode get(String url) throws Exception {
        return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
