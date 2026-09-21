# IslandBridgeAmongUs

Plugin Among Us yang lengkap untuk Spigot/Paper **1.20.1**. Tiada dependency luar (ProtocolLib tidak diperlukan).

## Ciri-ciri

| Ciri | Keterangan |
|---|---|
| Peranan | Impostor & Crewmate, bilangan impostor mengikut jumlah pemain |
| Bunuh | Impostor pukul crewmate, ada cooldown dan teleport ke mangsa |
| Mayat | Kepala pemain sebenar tergeletak di tempat kematian |
| Lapor | Klik kanan mayat untuk memulakan mesyuarat |
| Butang kecemasan | Blok khas untuk memanggil mesyuarat, had per pemain |
| Mesyuarat | Fasa berbual berjadual, pergerakan dibekukan, bossbar pemasa |
| **Undian** | GUI kepala pemain + butang "Langkau", kiraan undi, seri = tiada singkiran |
| Ejection | Boleh dedahkan sama ada yang dilontar itu impostor (`confirm-ejects`) |
| **Chat hantu** | Hantu hanya boleh berbual sesama hantu; pemain hidup hanya boleh chat semasa mesyuarat |
| Hantu | Tidak nampak oleh pemain hidup, boleh terbang, masih boleh menyiapkan tugasan |
| Tugasan | Titik tugasan diberikan secara rawak, ada mini-game GUI |
| Bar tugasan | Bossbar kemajuan tugasan untuk semua pemain |
| **Sabotaj** | Matikan lampu (buta) dan kebocoran reaktor (kira detik, impostor menang jika gagal dibaiki) |
| **Vent** | Impostor menunduk + klik kanan untuk berpindah antara vent |
| Kemenangan | Semua tugasan siap, semua impostor disingkir, impostor menyamai crewmate, atau sabotaj berjaya |

## Cara bina
Muat naik ke GitHub, buka tab **Actions**, muat turun artifact `IslandBridge-Plugin`.

## Pemasangan
Letak `IslandBridgeAmongUs.jar` dalam folder `plugins/`, kemudian restart server.

## Persediaan peta (sekali sahaja)

```
/ib set lobby        # berdiri di lobi
/ib set meeting      # berdiri di meja mesyuarat
/ib set button       # pandang blok butang kecemasan
/ib set lights       # pandang blok suis lampu
/ib set reactor      # pandang blok baik pulih reaktor
/ib addtask          # pandang setiap blok tugasan, ulang untuk setiap titik
/ib addvent          # pandang setiap blok vent, ulang (perlu 2 atau lebih)
/ib list             # semak semua tetapan
```

Kemudian `/ib start` untuk bermula (minimum 3 pemain).

## Arahan

**Semua pemain**
| Arahan | Fungsi |
|---|---|
| `/ib role` | Peranan, progres tugasan dan status anda |
| `/ib tasks` | Senarai koordinat tugasan anda |
| `/ib sabotage` | Menu sabotaj (impostor sahaja) |

**Admin** (`islandbridge.admin`, default OP)
| Arahan | Fungsi |
|---|---|
| `/ib start` / `/ib stop` | Mula / henti permainan |
| `/ib set <jenis>` | Tetapkan lokasi peta |
| `/ib addtask` / `/ib addvent` | Tambah titik tugasan / vent |
| `/ib cleartasks` / `/ib clearvents` | Padam semua |
| `/ib list` | Semak tetapan peta |
| `/ib reload` | Muat semula config |

Alias: `/amongus`, `/ibau`.

## Cara main

**Crewmate** — klik kanan blok tugasan yang diberikan, selesaikan mini-game, ulang sehingga bar tugasan penuh. Klik kanan mayat untuk melapor.

**Impostor** — pukul crewmate untuk membunuh, klik kanan **Kompas** untuk menu sabotaj, menunduk + klik kanan vent untuk berpindah. Anda juga boleh berpura-pura membuat tugasan.

**Hantu** — tidak nampak oleh yang hidup, boleh terbang, boleh menghabiskan tugasan, dan hanya boleh berbual dengan hantu lain. Hantu tidak boleh mengundi atau melapor.

## Tetapan (`plugins/IslandBridgeAmongUs/config.yml`)

```yaml
settings:
  min-players: 3
  impostors-small: 1
  impostors-large: 2
  large-threshold: 7
  tasks-per-crewmate: 3
  kill-cooldown-seconds: 20
  discussion-seconds: 45
  voting-seconds: 30
  emergency-meetings-per-player: 1
  confirm-ejects: true
  chat-only-in-meeting: true
  sabotage-cooldown-seconds: 30
  lights-seconds: 30
  reactor-seconds: 45
```

## Nota
- Jumlah titik tugasan mesti sekurang-kurangnya sama dengan `tasks-per-crewmate`. Jika kurang, plugin akan turunkan bilangan tugasan secara automatik dan memberi amaran.
- Semasa permainan, pemain tidak boleh pecah/letak blok, buang item, atau lapar.
