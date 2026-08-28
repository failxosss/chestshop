# SimpleShop

Vlastní plugin pro Minecraft server (Spigot/Paper). Umožňuje zakládat obchody
pomocí cedulky přiložené na truhlu.

## Formát cedulky

Hráč napíše na cedulku:

```
[shop]
100
B
```

- **1. řádek:** `[shop]` (velikost písmen nevadí)
- **2. řádek:** cena za 1 kus
- **3. řádek:** `B` = majitel **vykupuje** (hráči prodávají do shopu), `S` = majitel **prodává** (hráči kupují ze shopu)
- **4. řádek:** nech prázdný — plugin si ho sám doplní podle itemu, který je v truhle za/pod cedulí

### Podmínka
Do truhly, na kterou ceduli stavíš, musí být **předem vložený alespoň 1 kus**
itemu, který chceš prodávat/vykupovat — podle něj plugin pozná, o jaký item jde.

### Ovládání
Klik pravým tlačítkem na cedulku **neprovede nákup rovnou** — otevře se malé
GUI okno, kde hráč vidí prostředním slotem přesně to, co se kupuje/prodává,
včetně ceny, skladu a instrukce. Teprve kliknutím na ten item v GUI proběhne
transakce:
- Klik na item v GUI = koupě/prodej **1 kusu**
- Shift + klik na item v GUI = koupě/prodej **celého stacku**

GUI se po každé transakci samo aktualizuje (např. nový stav skladu), takže
hráč může klikat opakovaně bez zavírání okna. Ostatní sloty jsou vyplněné
šedým sklem jen jako dekorace — nedá se do nich nic vkládat ani z nich brát.

### Ochrana
Ceduli shopu může zbourat jen její majitel nebo op/admin (permission `simpleshop.admin`).

## Požadavky na serveru
- Paper nebo Spigot server, verze 1.20.x (Java 17+)
- Nainstalovaný **Vault**: https://www.spigotmc.org/resources/vault.34315/
- Nějaký ekonomický plugin fungující přes Vault, např. **EssentialsX** (Essentials + EssentialsX Economy)

Bez Vaultu + ekonomického pluginu se SimpleShop při startu serveru sám vypne
(uvidíš to v konzoli/logu).

## Jak plugin sestavit (zkompilovat do .jar)

Zdrojové kódy nejsou zkompilovaný plugin — je potřeba je sestavit přes Maven.

1. Nainstaluj si **Java 17** (nebo novější) a **Maven** (https://maven.apache.org/download.cgi)
2. Otevři terminál/příkazovou řádku ve složce `SimpleShop` (tam kde je `pom.xml`)
3. Spusť:
   ```
   mvn clean package
   ```
4. Po dokončení najdeš hotový plugin v `target/SimpleShop.jar`
5. Tenhle `.jar` soubor nakopíruj do složky `plugins` na tvém Minecraft serveru
6. Restartuj server

Maven si sám při buildu stáhne potřebné knihovny (Paper API, Vault API) z internetu,
takže build musí proběhnout na počítači/serveru, kde je přístup na internet.

## Podpora shulkerů, ItemsAdder a jiných custom itemů
Plugin nepozná item jen podle typu (např. "DIAMOND"), ale uloží si při vytvoření
cedule **kopii celého itemu včetně NBT dat** (obsah shulker boxu, enchanty,
vlastní jméno, custom model data z ItemsAdderu apod.). Při nákupu/prodeji pak
porovnává a vydává/bere přesně tenhle item — funguje to tedy i pro:
- shulker boxy, do kterých jsi předem naskládal věci (obsah zůstane zachovaný)
- itemy z ItemsAdderu a podobných pluginů (custom model data, NBT tagy)
- enchantnuté nebo přejmenované itemy

**Jak se pozná shoda:** pokud hráč prodává do shopu (režim B), musí mít v inventáři
item, který je **naprosto identický** s tím, co je uložené na cedulce (stejný typ,
stejné NBT/obsah). Např. shop na "shulker se startovacím kitem" vykoupí jen
takový shulker, který má úplně stejný obsah jako ten vzorový — ne libovolný
prázdný nebo jinak naplněný shulker.

## Poznámky / co se dá případně doladit
- Aktuálně je verze API nastavená na `1.20.4` v `pom.xml` — pokud máš jinou verzi
  serveru, uprav řádek `<version>1.20.4-R0.1-SNAPSHOT</version>` u `paper-api`
  na svou verzi (podporuje se i o verzi níž/výš, plugin se zpětně kompatibilní).
- Momentálně jeden shop = jeden druh (přesný) itemu (stejně jako u ChestShopu).
- Pokud by ses chtěl, abych přidal třeba omezení množství na cedulce
  (např. "100 za 5 kusů" místo za 1 kus), napiš mi a doplním to.
