# BuyForTime

App Android che converte le spese in tempo di lavoro: per ogni acquisto mostra quante ore servono per pagarlo,
in base allo stipendio inserito. Registra le spese, mostra il saldo dei buoni pasto e riceve le spese inviate
dall'app [BuoniPasto](https://github.com/Sbruso09/BuoniPasto).

> Il codice di questo repository è stato ricostruito dall'APK della versione 4.2.1, perché i sorgenti originali
> sono andati persi. Le versioni precedenti sono descritte nel [CHANGELOG](CHANGELOG.md).

## Compilazione

Apri la cartella in Android Studio (JDK 17) oppure esegui `./gradlew assembleRelease`.
L'APK viene compilato anche da GitHub Actions a ogni modifica.

### Firma

La chiave di firma **non** è nel repository. Per firmare con la propria chiave metti il file in
`app/buyfortime.keystore` e la password in `~/.gradle/gradle.properties` come `buyfortimePassword=...`
(oppure nella variabile d'ambiente `BUYFORTIME_KEY_PASSWORD`). Su GitHub Actions si usano i secret
`BUYFORTIME_KEYSTORE_BASE64` e `BUYFORTIME_KEY_PASSWORD`. Senza chiave l'APK viene firmato con la chiave di debug.

## Licenza

Copyright © 2026 Mario Borruso. **Tutti i diritti riservati.** Il codice è pubblico solo per essere consultato:
copia, modifica, distribuzione e qualsiasi uso, anche commerciale, richiedono un'autorizzazione scritta.
Vedi [LICENSE](LICENSE).
