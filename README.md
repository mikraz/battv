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
| `worker.js` | Il ponte https da mettere su Cloudflare Workers, necessario se il server del provider è in http. |

## Il ponte https

Una pagina pubblicata in https non può collegarsi a un server in http: il browser blocca la richiesta come "contenuto misto". Il ponte riceve le richieste in https e le gira al provider.

1. Su Cloudflare crea un Worker e incolla il contenuto di `worker.js`.
2. Cambia `KEY` con una chiave tua. Non pubblicare la chiave reale in questo repository.
3. Nel player, campo **Ponte**: `https://NOME.TUOACCOUNT.workers.dev/?k=LA_TUA_CHIAVE`

## Pubblicazione con GitHub Pages

Settings → Pages → Source: *Deploy from a branch* → branch `main`, cartella `/ (root)`. Il sito sarà su `https://mikraz.github.io/battv/`.
