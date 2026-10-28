package com.galactic.spacefarer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class SpacefarerIntegrationTest {
    private static final String BASE = "/odata/v4/spacefarer/";
    private static final String ENTITY = BASE + "Spacefarers";
    private static final String X_ID = "11111111-1111-4111-8111-111111111111";
    private static final String Y_ID = "33333333-3333-4333-8333-333333333333";

    @Autowired MockMvc http;
    @MockitoBean JavaMailSender mail;

    @Test
    @DisplayName("Bejelentkezés és megfelelő szerepkör szükséges")
    void authenticationIsRequired() throws Exception {
        http.perform(get(ENTITY)).andExpect(status().isUnauthorized());
        http.perform(get(ENTITY).with(httpBasic("pilot-x", "wrong"))).andExpect(status().isUnauthorized());
        http.perform(get(ENTITY).with(httpBasic("privileged", ""))).andExpect(status().isUnauthorized());
        http.perform(get(ENTITY).with(httpBasic("visitor", "local-visitor"))).andExpect(status().isForbidden());
        http.perform(get("/spacefarers/index.html")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Más bolygó rekordjai nem olvashatók")
    void planetsCannotReadEachOthersRecords() throws Exception {
        http.perform(get(ENTITY).queryParam("$filter", "ID eq " + Y_ID).with(httpBasic("pilot-x", "local-x")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").isEmpty());
        http.perform(get(active(Y_ID)).with(httpBasic("pilot-x", "local-x"))).andExpect(status().is4xxClientError());
        http.perform(get(active(X_ID)).with(httpBasic("pilot-y", "local-y"))).andExpect(status().is4xxClientError());
        http.perform(get(active(Y_ID)).with(httpBasic("pilot-y", "local-y")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Luna Sol"));
    }

    @Test
    @DisplayName("Szűrés, rendezés és lapozás közben is érvényes a bolygókorlátozás")
    void filteringSortingPaginationAndExpansionStayScoped() throws Exception {
        http.perform(get(ENTITY).with(httpBasic("pilot-x", "local-x"))
                .queryParam("$filter", "IsActiveEntity eq true and stardustCollection ge 100")
                .queryParam("$orderby", "fullName asc").queryParam("$top", "1").queryParam("$skip", "0")
                .queryParam("$count", "true").queryParam("$expand", "originPlanet,department,position"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value.length()").value(1))
                .andExpect(jsonPath("$.value[0].originPlanet.code").value("X"));
        http.perform(get(BASE + "Planets").with(httpBasic("pilot-x", "local-x")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value.length()").value(1))
                .andExpect(jsonPath("$.value[0].code").value("X"));
    }

    @Test
    @DisplayName("Más bolygó rekordjai nem módosíthatók")
    void foreignWritesAndPlanetChangesAreRejected() throws Exception {
        http.perform(patch(active(Y_ID)).with(httpBasic("pilot-x", "local-x"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"spacesuitColor\":\"Red\"}"))
                .andExpect(status().is4xxClientError());
        http.perform(delete(active(Y_ID)).with(httpBasic("pilot-x", "local-x"))).andExpect(status().is4xxClientError());
        http.perform(patch(active(X_ID)).with(httpBasic("pilot-x", "local-x"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"originPlanet_code\":\"Y\"}"))
                .andExpect(status().isForbidden());
        http.perform(post(ENTITY).with(httpBasic("pilot-x", "local-x"))
                .contentType(MediaType.APPLICATION_JSON).content(candidate("Foreign", "Y")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mail);
    }

    @Test
    @DisplayName("Bolygó nélküli felhasználó nem kap adathozzáférést")
    void missingPlanetAttributeFailsClosed() throws Exception {
        http.perform(get(ENTITY).with(httpBasic("unassigned", "local-unassigned")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").isEmpty());
        http.perform(post(ENTITY).with(httpBasic("unassigned", "local-unassigned"))
                .contentType(MediaType.APPLICATION_JSON).content(candidate("Unassigned", "X")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A létrehozás kitölti az alapértékeket és üdvözlőlevelet küld")
    void createEnhancesDataAndSendsWelcomeEmail() throws Exception {
        String body = http.perform(post(ENTITY).with(httpBasic("pilot-x", "local-x"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"IsActiveEntity":true,"fullName":"  Test Pilot  ","email":"PILOT@example.test",
                     "wormholeNavigationSkill":80,"originPlanet_code":"X"}
                    """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fullName").value("Test Pilot"))
                .andExpect(jsonPath("$.email").value("pilot@example.test"))
                .andExpect(jsonPath("$.stardustCollection").value(0))
                .andExpect(jsonPath("$.spacesuitColor").value("White"))
                .andExpect(jsonPath("$.navigationRank").value("Expert"))
                .andReturn().getResponse().getContentAsString();
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("pilot@example.test");
        assertThat(sent.getValue().getText()).contains("Test Pilot", "Congratulations");
        String id = JsonPath.read(body, "$.ID");
        http.perform(patch(active(id)).with(httpBasic("pilot-x", "local-x"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"spacesuitColor\":\"Blue\"}"))
                .andExpect(status().isOk());
        http.perform(get(active(id)).with(httpBasic("pilot-x", "local-x")))
                .andExpect(jsonPath("$.spacesuitColor").value("Blue"))
                .andExpect(jsonPath("$.wormholeNavigationSkill").value(80));
        http.perform(delete(active(id)).with(httpBasic("pilot-x", "local-x"))).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Hibás adatokkal nem megy ki levél")
    void invalidCandidatesDoNotSendEmail() throws Exception {
        for (String change : new String[]{"\"stardustCollection\":-1", "\"wormholeNavigationSkill\":101",
                "\"email\":\"not-an-email\"", "\"fullName\":\"   \""}) {
            String body = candidate("Invalid", "X");
            if (change.startsWith("\"email\"")) body = body.replace("\"email\":\"test@example.test\"", change);
            else if (change.startsWith("\"fullName\"")) body = body.replace("\"fullName\":\"Invalid\"", change);
            else body = body.substring(0, body.length() - 1) + "," + change + "}";
            http.perform(post(ENTITY).with(httpBasic("pilot-x", "local-x"))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(mail);
    }

    @Test
    @DisplayName("Piszkozatból csak véglegesítéskor megy ki levél")
    void draftSendsEmailOnlyWhenActivated() throws Exception {
        String body = http.perform(post(ENTITY).with(httpBasic("pilot-x", "local-x"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.originPlanet_code").value("X"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.ID");
        String draft = ENTITY + "(ID=" + id + ",IsActiveEntity=false)";
        verifyNoInteractions(mail);
        http.perform(get(draft).with(httpBasic("pilot-y", "local-y"))).andExpect(status().is4xxClientError());
        http.perform(patch(draft).with(httpBasic("pilot-y", "local-y"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"spacesuitColor\":\"Red\"}"))
                .andExpect(status().is4xxClientError());
        http.perform(patch(draft).with(httpBasic("pilot-x", "local-x"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Draft Pilot\",\"email\":\"draft@example.test\",\"spacesuitColor\":\"Silver\"}"))
                .andExpect(status().isOk());
        verifyNoInteractions(mail);
        http.perform(post(draft + "/SpacefarerService.draftActivate").with(httpBasic("pilot-x", "local-x"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(mail).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Visszavont tranzakció nem küld levelet")
    void rolledBackBatchDoesNotSendWelcomeEmail() throws Exception {
        String batch = "--batch\r\nContent-Type: multipart/mixed; boundary=changeset\r\n\r\n"
                + batchPart(1, candidate("Rollback Pilot", "X"))
                + batchPart(2, candidate("Invalid Batch", "X").replace("\"IsActiveEntity\":true", "\"IsActiveEntity\":true,\"stardustCollection\":-1"))
                + "--changeset--\r\n--batch--\r\n";
        String response = http.perform(post(BASE + "$batch").with(httpBasic("pilot-x", "local-x"))
                .contentType("multipart/mixed; boundary=batch").content(batch))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).contains("400 Bad Request");
        verifyNoInteractions(mail);
        http.perform(get(ENTITY).with(httpBasic("pilot-x", "local-x"))
                .queryParam("$filter", "fullName eq 'Rollback Pilot'"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").isEmpty());
    }

    @Test
    @DisplayName("A levélküldés hibája nem vonja vissza a mentést")
    void smtpFailureDoesNotUndoTheSavedCandidate() throws Exception {
        doThrow(new MailSendException("teszt SMTP-hiba")).when(mail).send(any(SimpleMailMessage.class));
        http.perform(post(ENTITY).with(httpBasic("pilot-x", "local-x"))
                .contentType(MediaType.APPLICATION_JSON).content(candidate("Mail Failure", "X")))
                .andExpect(status().isCreated());
    }

    private String active(String id) { return ENTITY + "(ID=" + id + ",IsActiveEntity=true)"; }

    private String batchPart(int index, String body) {
        return "--changeset\r\nContent-Type: application/http\r\nContent-Transfer-Encoding: binary\r\n"
                + "Content-ID: " + index + "\r\n\r\nPOST Spacefarers HTTP/1.1\r\n"
                + "Content-Type: application/json\r\n\r\n" + body + "\r\n";
    }

    private String candidate(String name, String planet) {
        return "{\"IsActiveEntity\":true,\"fullName\":\"" + name
                + "\",\"email\":\"test@example.test\",\"originPlanet_code\":\"" + planet + "\"}";
    }
}
