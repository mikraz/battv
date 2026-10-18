# BatTV

Player IPTV leggero in un solo file HTML, pensato per il browser della Tesla e per qualsiasi altro browser.

## Cosa fa

- Accesso Xtream (server, username, password) oppure caricamento di un file M3U
- Sezioni Live, Film e Serie TV con le categorie del provider
- Ricerca, stagioni ed episodi per le serie
- Preferiti per canali, film e serie in una schermata dedicata
- Categorie riordinabili e nascondibili
- Mini player per fare zapping mentre sfogli, più Picture in Picture dove il browser lo supporta

Preferiti, ordine e categorie nascoste sono salvati nel browser di ciascun dispositivo.

## File

| File | A cosa serve |
|---|---|
| `index.html` | Il player. Si pubblica così com'è su GitHub Pages o Netlify. |
| `android/` | L'app per Android TV, che usa lo stesso `index.html`. |
| `apk.html` | Link breve per scaricare l'ultima versione dell'app. |
| `worker.js` | Il ponte https da mettere su Cloudflare Workers, necessario se il server del provider è in http o per leggere il DNS del provider. |

## Il ponte https

Una pagina pubblicata in https non può collegarsi a un server in http: il browser blocca la richiesta come "contenuto misto". Il ponte riceve le richieste in https e le gira al provider.

1. Su Cloudflare crea un Worker e incolla il contenuto di `worker.js`.
2. Cambia `KEY` con una chiave tua. Non pubblicare la chiave reale in questo repository.
3. Nel player, campo **Ponte**: `https://NOME.TUOACCOUNT.workers.dev/?k=LA_TUA_CHIAVE`

## Server che cambia

Alcuni provider cambiano spesso l'indirizzo del server e lo pubblicano in un file DNS, di solito un `dns.php` che restituisce `{"servers":[{"dns":"http://..."}]}`.

1. Nel player, campo **Indirizzo DNS del provider**: incolla l'indirizzo del `dns.php`. Il campo **Server** può restare vuoto.
2. Inserisci anche il **Ponte**: di solito il `dns.php` non permette la lettura diretta da un'altra pagina, e il ponte la fa al posto del browser.

All'accesso BatTV legge il server dal DNS, prova prima la versione https e poi quella http. Se durante l'uso il server smette di rispondere, o un canale non parte, rilegge il DNS, passa al server nuovo e riprova una volta. Preferiti, ordine e categorie nascoste restano legati all'account, non al server, quindi non si perdono quando il server cambia.

Non pubblicare in questo repository l'indirizzo DNS o i dati del tuo provider: si inseriscono solo nel player.

## App per Android TV

La cartella `android/` contiene un'app che mostra lo stesso `index.html` sul televisore, con in più:

- **telecomando**: frecce per spostarsi, OK per scegliere, Indietro per tornare (Indietro due volte per uscire);
- **player nativo ExoPlayer**: legge HLS, TS, MP4 e MKV, con audio AC3 se il box lo supporta;
- **ponte interno**: l'app legge DNS e server in http da sola, quindi il campo Ponte non serve e non compare.

### Installazione

Sul box installa l'app **Downloader** dal Play Store e apri `https://mikraz.github.io/battv/apk.html`: scarica l'ultima versione. La prima volta Android chiede di permettere a Downloader di installare app.

### Compilazione automatica

A ogni modifica di `index.html` o di `android/` GitHub compila l'APK e lo pubblica tra le Release. Servono due secret in *Settings → Secrets and variables → Actions*:

| Secret | Contenuto |
|---|---|
| `BATTV_KEYSTORE_B64` | La chiave di firma `battv.keystore` codificata in base64 |
| `BATTV_KEYSTORE_PASSWORD` | La sua password |

La chiave deve restare sempre la stessa: Android accetta un aggiornamento solo se è firmato con la chiave della versione installata. Non metterla mai nel repository.

## Pubblicazione con GitHub Pages

Settings → Pages → Source: *Deploy from a branch* → branch `main`, cartella `/ (root)`. Il sito sarà su `https://mikraz.github.io/battv/`.
