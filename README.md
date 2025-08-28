# OkretniStolBT (Android, Kotlin)

Jednostavna Android aplikacija za upravljanje okretnim stolom preko **Bluetooth SPP** veze (ESP32 Serial BT).

## Što radi
- Spaja se na **upareni** BT uređaj (iz liste uparenih).
- Šalje jednostavne tekstualne naredbe, svaka završena s `\n`:
  - `PLAY`, `STOP`, `CW`, `CCW`
  - `SPEED +`, `SPEED -`
  - `ANGLE +`, `ANGLE -`
  - bilo koja **custom** naredba iz polja

## Zahtjevi
- ESP32 treba oglašavati **Serial Bluetooth (SPP)** i čitati linije (npr. preko `BluetoothSerial` biblioteke). Standardni SPP UUID: `00001101-0000-1000-8000-00805F9B34FB`.
- Android 5.0+ (minSdk 21), targetSdk 34. Na Androidu 12+ aplikacija traži `BLUETOOTH_CONNECT/SCAN` runtime dozvole, na starijem `ACCESS_FINE_LOCATION`.

## Upute
1. Uključite BT na mobitelu i **uparite** se s ESP32 u *System Settings* (izvan aplikacije).
2. Pokrenite aplikaciju -> **Connect to Device** -> odaberite upareni uređaj.
3. Pritiskom na tipke šalju se naredbe. U dnu se vidi *Log*.
4. Polje *Custom command* šalje custom liniju (npr. `SPEED 3` ili `ANGLE 90`).

## Prilagodba
- Komande možete urediti u `MainActivity.kt` (`send("...")`).
- Ako vaš ESP32 koristi drugačiji mehanizam ili treba CRLF, promijenite `BluetoothHelper.sendLine()`.

## Napomena
- Aplikacija ne skenira za nove uređaje (prikazuje **samo uparene**), što je jednostavnije i stabilnije.
