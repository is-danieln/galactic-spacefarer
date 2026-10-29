package com.galactic.spacefarer;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.draft.DraftNewEventContext;
import com.sap.cds.services.draft.DraftPatchEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.changeset.ChangeSetListener;
import com.sap.cds.services.request.UserInfo;

import cds.gen.spacefarerservice.Spacefarers;
import cds.gen.spacefarerservice.Spacefarers_;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

@Component
@ServiceName("SpacefarerService")
public class SpacefarerHandler implements EventHandler {
    private final WelcomeEmail welcomeEmail;

    public SpacefarerHandler(WelcomeEmail welcomeEmail) {
        this.welcomeEmail = welcomeEmail;
    }

    @Before(entity = Spacefarers_.CDS_NAME)
    public void prepareDraft(DraftNewEventContext context, List<Spacefarers> candidates) {
        for (Spacefarers candidate : candidates) {
            assignPlanet(candidate, context.getUserInfo());
        }
    }

    @Before(entity = Spacefarers_.CDS_NAME)
    public void checkDraftPlanet(DraftPatchEventContext context, List<Spacefarers> candidates) {
        for (Spacefarers candidate : candidates) {
            checkPlanet(candidate, context.getUserInfo());
        }
    }

    @Before(entity = Spacefarers_.CDS_NAME)
    public void beforeCreate(CdsCreateEventContext context, List<Spacefarers> candidates) {
        for (Spacefarers candidate : candidates) {
            assignPlanet(candidate, context.getUserInfo());
            validateAndEnhance(candidate, true);
        }
    }

    @Before(entity = Spacefarers_.CDS_NAME)
    public void beforeUpdate(CdsUpdateEventContext context, List<Spacefarers> candidates) {
        for (Spacefarers candidate : candidates) {
            // csak a beküldött mezőket módosítjuk
            checkPlanet(candidate, context.getUserInfo());
            validateAndEnhance(candidate, false);
        }
    }

    @After(entity = Spacefarers_.CDS_NAME)
    public void afterCreate(CdsCreateEventContext context, List<Spacefarers> candidates) {
        for (Spacefarers candidate : candidates) {
            String email = candidate.getEmail();
            String name = candidate.getFullName();
            // az @After még a tranzakción belül fut, megvárjuk a végleges mentést
            context.getChangeSetContext().register(new ChangeSetListener() {
                @Override
                public void afterClose(boolean completed) {
                    if (completed) {
                        welcomeEmail.send(email, name);
                    }
                }
            });
        }
    }

    private void assignPlanet(Spacefarers candidate, UserInfo user) {
        checkPlanet(candidate, user);
        candidate.setOriginPlanetCode(user.getAttributeValues("planet").getFirst());
    }

    private void checkPlanet(Spacefarers candidate, UserInfo user) {
        List<String> planets = user.getAttributeValues("planet");
        if (planets == null || planets.size() != 1) {
            throw new ServiceException(ErrorStatuses.FORBIDDEN, "Your account must belong to exactly one planet.");
        }
        String submittedPlanet = candidate.getOriginPlanetCode();
        if (submittedPlanet != null && !planets.getFirst().equals(submittedPlanet)) {
            throw new ServiceException(ErrorStatuses.FORBIDDEN, "You can only manage spacefarers from your own planet.");
        }
        if (candidate.containsKey("originPlanet")) {
            throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Use originPlanet_code to select a planet.");
        }
    }

    private void validateAndEnhance(Spacefarers candidate, boolean creating) {
        if (creating || candidate.containsKey("fullName")) {
            candidate.setFullName(requiredText(candidate.getFullName(), "Name"));
        }
        if (creating || candidate.containsKey("email")) {
            String email = requiredText(candidate.getEmail(), "Email address").toLowerCase(Locale.ROOT);
            try {
                InternetAddress address = new InternetAddress(email, true);
                address.validate();
                if (!email.equals(address.getAddress()) || !email.contains("@")) {
                    throw new AddressException();
                }
            } catch (AddressException e) {
                throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Enter a valid email address.");
            }
            candidate.setEmail(email);
        }
        if (creating || candidate.containsKey("spacesuitColor")) {
            String color = candidate.getSpacesuitColor();
            candidate.setSpacesuitColor(creating && color == null ? "White" : requiredText(color, "Spacesuit color"));
        }
        if (creating && candidate.getStardustCollection() == null) {
            candidate.setStardustCollection(0);
        }
        if (creating && candidate.getWormholeNavigationSkill() == null) {
            candidate.setWormholeNavigationSkill(10);
        }
        if (candidate.containsKey("stardustCollection")) {
            range(candidate.getStardustCollection(), 0, 1_000_000, "Stardust collection");
        }
        if (candidate.containsKey("wormholeNavigationSkill")) {
            int skill = range(candidate.getWormholeNavigationSkill(), 0, 100, "Navigation skill");
            candidate.setNavigationRank(skill >= 75 ? "Expert" : skill >= 40 ? "Navigator" : "Cadet");
        }
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(ErrorStatuses.BAD_REQUEST, field + " is required.");
        }
        return value.strip();
    }

    private int range(Integer value, int minimum, int maximum, String field) {
        if (value == null || value < minimum || value > maximum) {
            throw new ServiceException(ErrorStatuses.BAD_REQUEST, field + " must be between " + minimum + " and " + maximum + ".");
        }
        return value;
    }
}
