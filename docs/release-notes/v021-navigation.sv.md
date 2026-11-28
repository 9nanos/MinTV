# Min TV 0.21.0-beta.1 (QA)

## Nytt och ändrat

**Enklare navigering**
- Film och Serier är nu egna flikar i toppmenyn. Undermenyn Film/Serier är borta.
- Varje flik består bara av rader: Fortsätt titta, Favoriter, Senast sett, leverantörens mappar och tips. Favoriter, Senast sett och mapparna slutar med "Visa alla". En tom flik visar en Sök-knapp.
- Inställningar har fem grupper i stället för tretton: Profil och uppdatering, Källor och tv-guide, Utseende, Uppspelning samt Sport och system. Varje grupp är en sida.
- "Bibliotek och historik" är borttaget. Säkerhetskopia, Lokal synk, Fellogg, Om appen och Utvecklarverktyg finns under Sport och system och går att hitta med sökningen. PIN-koden för Inställningar skyddar dem som tidigare.
- Uppdatera appen: första raden under Profil, och knappen längst till höger i toppmenyn heter nu "Uppdatera · v…".

**Snabbare och lugnare**
- Film- och Serier-rutnätet ritar inte längre om alla synliga affischer vid varje knapptryck. Bara den affisch du lämnar och den du landar på uppdateras.
- Film- och Serier-raderna läses inte om från katalogen varje gång appen sparar filminfo eller uppspelningsposition i bakgrunden. Det sker högst en gång per 30 sekunder. Nya favoriter och nysynkade kataloger syns fortfarande direkt.

**Från officiella OwnTV 5.2.3 (Core 1.0.72)**
- Bild-i-bild, autostart och röstsökning.
- Lås för kategorier och PIN-kod för Inställningar.
- Tv-guiden: dagssida per program och förhandsvisning med OK innan du byter kanal.
- Ljust tema som alternativ till det mörka.
- Undertextspråk som valts tidigare väljs även när undertexterna dyker upp sent.

**Nytt app-ID och ny signeringsnyckel**
- QA-appen heter nu tv.mintv.app.qa och är signerad med en ny nyckel. Den installeras som en ny app bredvid den gamla, och inställningar följer inte med automatiskt. Använd Säkerhetskopia i den gamla appen och återställ i den nya.

## Att kontrollera på enheten
- Gör en säkerhetskopia i den gamla appen innan du installerar.
- Film och Serier: bläddra snabbt med fjärrkontrollen i en stor katalog och kontrollera att det inte hackar.
- "Visa alla" på Favoriter, Senast sett och en mapp öppnar rätt lista, och Bakåt går tillbaka till raden.
- Inställningar: alla fem grupper går att öppna, och Säkerhetskopia, Lokal synk, Fellogg och Om öppnas och stängs med Bakåt.
- Uppdatera-knappen i toppmenyn och raden under Profil öppnar uppdateringsrutan.
- Twitch kan behöva aktiveras igen efter bytet av app-ID.

Verifierat i CI: alla Min TV-jobb (bygge, enhetstester, lint, uppstart, Home-, signerings- och uppgraderingstester), i18n och den syntetiska prestandaresan.
