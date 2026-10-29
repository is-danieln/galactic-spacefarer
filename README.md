# Galactic Spacefarers

SAP CAP Java, Fiori elements és SQLite.

## Indítás

Java 21+ és Docker kell hozzá. Az első fordításhoz és a felülethez internet szükséges.

```sh
docker compose up -d
./scripts/maven.sh spring-boot:run
```

Alkalmazás: http://localhost:8080/spacefarers/index.html — a lista a **Go** gombbal tölthető be.
Tesztpostaláda: http://localhost:8025

| Felhasználó | Jelszó | Bolygó |
| --- | --- | --- |
| pilot-x | local-x | X |
| pilot-y | local-y | Y |

Felhasználóváltáshoz nyiss privát ablakot. Ha a Mailpit helyben telepítve van, a `./start.sh` Docker nélkül is elindítja az alkalmazást és a postaládát.

## Tesztek

```sh
./scripts/maven.sh verify
```

A böngészős teszthez fusson az alkalmazás és a Mailpit. Node.js 22.12+ mellett: `npm ci`, `npx playwright install chromium`, majd `npm run test:ui`.

## Tudnivalók

- A backend ellenőrzi a szerepkört és a bolygónkénti hozzáférést, a piszkozatoknál is.
- Csillagpor: 0–1 000 000. Navigációs képesség: 0–100. Alapértékük 0 és 10.
- Üdvözlőlevél csak az új rekord végleges mentése után megy ki. Piszkozat és módosítás nem küld levelet.
- A Mailpit helyben fogja meg a leveleket. Küldési hibánál naplózás van, automatikus újrapróbálás nincs.
- Az SQLite memóriában fut, újraindításkor visszaállnak a mintaadatok.
- Helyi tesztfelhasználók vannak. Éles hitelesítés és BTP-telepítés nincs beállítva.

SMTP-beállítások: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `MAIL_FROM`.
